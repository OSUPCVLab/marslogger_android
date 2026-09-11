package edu.osu.pcv.marslogger.benchmark;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Low-overhead instrumentation for the ROS point-cloud to OpenGL path. File I/O is delegated to
 * a bounded background writer. All times are in the elapsedRealtimeNanos clock domain.
 */
public final class PipelinePerformanceLogger {
    public static final String CSV_HEADER =
            "session_id,elapsed_time_s,event,frame_id,input_frame_id,sensor_timestamp_ns," +
            "android_receive_ns,preprocessing_complete_ns,processing_mapping_complete_ns," +
            "opengl_handoff_ns,render_start_ns,render_end_ns,input_point_count," +
            "processed_point_count,queue_depth,received_frames,processed_frames," +
            "skipped_frames,dropped_frames,logger_queue_drops,processing_hz,opengl_fps," +
            "processing_latency_ms,receipt_to_render_latency_ms," +
            "mapped_receipt_to_render_latency_ms,processing_latency_mean_ms," +
            "processing_latency_median_ms,processing_latency_p95_ms," +
            "receipt_to_render_latency_mean_ms,receipt_to_render_latency_median_ms," +
            "receipt_to_render_latency_p95_ms,mapped_receipt_to_render_latency_mean_ms," +
            "mapped_receipt_to_render_latency_median_ms," +
            "mapped_receipt_to_render_latency_p95_ms,source_skipped_rate," +
            "render_dropped_rate,dropped_skipped_rate";

    private static final int RAW_HISTORY_LIMIT = 512;
    private static final long MISSING_LONG = Long.MIN_VALUE;

    private final MonotonicClock clock;
    private final FrameDropTracker sourceFrames = new FrameDropTracker();
    private final LatencyStatistics processingLatency = new LatencyStatistics();
    private final LatencyStatistics receiptToRenderLatency = new LatencyStatistics();
    private final LatencyStatistics mappedReceiptToRenderLatency = new LatencyStatistics();
    private final LinkedHashMap<Long, RawFrame> rawBySequence = limitedMap();
    private final LinkedHashMap<Long, RawFrame> rawByStamp = limitedMap();

    private volatile boolean active;
    private volatile long generation;
    private BenchmarkSession session;
    private AsyncCsvWriter<PipelineRow> writer;
    private FrameTiming pendingRender;
    private long processedFrames;
    private long handoffFrames;
    private long droppedFrames;
    private long renderCount;
    private long loggerQueueDrops;
    private long firstProcessedNs;
    private long lastProcessedNs;
    private long firstRenderEndNs;
    private long lastRenderEndNs;

    public PipelinePerformanceLogger() {
        this(MonotonicClock.SYSTEM);
    }

    PipelinePerformanceLogger(MonotonicClock clock) {
        this.clock = clock;
    }

    public synchronized void start(BenchmarkSession newSession) {
        if (active) {
            return;
        }
        session = newSession;
        sourceFrames.clear();
        processingLatency.clear();
        receiptToRenderLatency.clear();
        mappedReceiptToRenderLatency.clear();
        rawBySequence.clear();
        rawByStamp.clear();
        pendingRender = null;
        processedFrames = 0;
        handoffFrames = 0;
        droppedFrames = 0;
        renderCount = 0;
        loggerQueueDrops = 0;
        firstProcessedNs = 0;
        lastProcessedNs = 0;
        firstRenderEndNs = 0;
        lastRenderEndNs = 0;
        writer = new AsyncCsvWriter<>(newSession.pipelineFile, CSV_HEADER, 2048);
        generation++;
        active = true;
    }

    public void stop() {
        AsyncCsvWriter<PipelineRow> writerToClose;
        synchronized (this) {
            if (!active) {
                return;
            }
            active = false;
            long now = clock.nowNanos();
            if (pendingRender != null) {
                droppedFrames++;
                if (!writer.offer(makeDroppedRow(pendingRender, now))) {
                    loggerQueueDrops++;
                }
                pendingRender = null;
            }
            PipelineRow summary = makeSummaryRow(now);
            if (!writer.offer(summary)) {
                loggerQueueDrops++;
            }
            writerToClose = writer;
            rawBySequence.clear();
            rawByStamp.clear();
        }
        writerToClose.closeAsync();
    }

    public boolean isActive() {
        return active;
    }

    boolean awaitStopped(long timeoutMs) throws InterruptedException {
        AsyncCsvWriter<PipelineRow> currentWriter = writer;
        return currentWriter == null || currentWriter.awaitClosed(timeoutMs);
    }

    synchronized long getDroppedFramesForTest() {
        return droppedFrames;
    }

    long getSkippedFramesForTest() {
        return sourceFrames.getSkipped();
    }

    /** Capture the first Java boundary after a raw ROS LiDAR scan is delivered. */
    public void onRawFrameReceived(long frameId, long sensorTimestampNs, int inputPointCount) {
        if (!active) {
            return;
        }
        long eventGeneration = generation;
        long receiveNs = clock.nowNanos();
        synchronized (this) {
            if (!active || eventGeneration != generation) {
                return;
            }
            sourceFrames.record(frameId);
            RawFrame raw = new RawFrame(frameId, sensorTimestampNs, receiveNs, inputPointCount);
            rawBySequence.put(frameId, raw);
            if (sensorTimestampNs > 0) {
                rawByStamp.put(sensorTimestampNs, raw);
            }
            PipelineRow row = new PipelineRow(session.sessionId, elapsedSeconds(receiveNs),
                    "RAW_RECEIVE", MISSING_LONG, frameId, sensorTimestampNs, receiveNs,
                    MISSING_LONG, MISSING_LONG, MISSING_LONG, MISSING_LONG, MISSING_LONG,
                    inputPointCount, -1, -1, sourceFrames.getReceived(), processedFrames,
                    sourceFrames.getSkipped(), droppedFrames, loggerQueueDrops, processingHz(),
                    fps(), Double.NaN, Double.NaN, Double.NaN, null, null, null,
                    sourceSkippedRate(), renderDroppedRate(), droppedSkippedRate());
            if (!writer.offer(row)) {
                loggerQueueDrops++;
            }
        }
    }

    /** Capture delivery of Faster-LIO's registered cloud, the observable mapping-complete edge. */
    public FrameTiming onMappingOutputReceived(long frameId, long sensorTimestampNs) {
        if (!active) {
            return null;
        }
        long eventGeneration = generation;
        long mappingCompleteNs = clock.nowNanos();
        synchronized (this) {
            if (!active || eventGeneration != generation) {
                return null;
            }
            RawFrame raw = sensorTimestampNs > 0 ? rawByStamp.remove(sensorTimestampNs) : null;
            if (raw == null) {
                raw = rawBySequence.remove(frameId);
            }
            if (raw != null) {
                rawBySequence.remove(raw.frameId);
                if (raw.sensorTimestampNs > 0) {
                    rawByStamp.remove(raw.sensorTimestampNs);
                }
            }
            FrameTiming timing = new FrameTiming(eventGeneration, frameId, sensorTimestampNs,
                    mappingCompleteNs, raw);
            processedFrames++;
            if (firstProcessedNs == 0) {
                firstProcessedNs = mappingCompleteNs;
            }
            lastProcessedNs = mappingCompleteNs;
            if (raw != null) {
                processingLatency.add(nanosToMillis(mappingCompleteNs - raw.receiveNs));
            }
            return timing;
        }
    }

    /** Capture completion of PointCloud2-to-render-point conversion. */
    public void onPreprocessingComplete(FrameTiming timing, int processedPointCount) {
        if (timing == null || !active || timing.generation != generation) {
            return;
        }
        timing.preprocessingCompleteNs = clock.nowNanos();
        timing.processedPointCount = processedPointCount;
    }

    /** Capture completion of renderer data preparation immediately before requestRender(). */
    public void onOpenGlHandoff(FrameTiming timing) {
        if (timing == null || !active || timing.generation != generation) {
            return;
        }
        long handoffNs = clock.nowNanos();
        synchronized (this) {
            if (!active || timing.generation != generation) {
                return;
            }
            timing.openglHandoffNs = handoffNs;
            timing.queueDepth = pendingRender == null ? 0 : 1;
            if (pendingRender != null) {
                droppedFrames++;
                if (!writer.offer(makeDroppedRow(pendingRender, handoffNs))) {
                    loggerQueueDrops++;
                }
            }
            pendingRender = timing;
            handoffFrames++;
        }
    }

    /** Called on the GL thread at the first instruction of onDrawFrame. */
    public RenderTiming onRenderStart() {
        if (!active) {
            return null;
        }
        long startNs = clock.nowNanos();
        synchronized (this) {
            if (!active) {
                return null;
            }
            FrameTiming frame = pendingRender;
            pendingRender = null;
            return new RenderTiming(generation, frame, startNs);
        }
    }

    /** Called on the GL thread after draw commands have been submitted (not display presentation). */
    public void onRenderEnd(RenderTiming render) {
        if (render == null || !active || render.generation != generation) {
            return;
        }
        long endNs = clock.nowNanos();
        PipelineRow row;
        AsyncCsvWriter<PipelineRow> currentWriter;
        synchronized (this) {
            if (!active || render.generation != generation) {
                return;
            }
            renderCount++;
            if (firstRenderEndNs == 0) {
                firstRenderEndNs = endNs;
            }
            lastRenderEndNs = endNs;
            if (render.frame != null) {
                render.frame.renderStartNs = render.startNs;
                render.frame.renderEndNs = endNs;
                double mappedLatency = nanosToMillis(endNs - render.frame.mappingCompleteNs);
                mappedReceiptToRenderLatency.add(mappedLatency);
                if (render.frame.raw != null) {
                    receiptToRenderLatency.add(nanosToMillis(endNs - render.frame.raw.receiveNs));
                }
            }
            row = makeRenderRow(render, endNs);
            currentWriter = writer;
        }
        if (!currentWriter.offer(row)) {
            synchronized (this) {
                loggerQueueDrops++;
            }
        }
    }

    private PipelineRow makeRenderRow(RenderTiming render, long endNs) {
        FrameTiming frame = render.frame;
        RawFrame raw = frame == null ? null : frame.raw;
        return new PipelineRow(session.sessionId, elapsedSeconds(endNs), "RENDER",
                frame == null ? MISSING_LONG : frame.frameId,
                raw == null ? MISSING_LONG : raw.frameId,
                raw == null ? (frame == null ? MISSING_LONG : frame.sensorTimestampNs)
                        : raw.sensorTimestampNs,
                raw == null ? MISSING_LONG : raw.receiveNs,
                frame == null ? MISSING_LONG : frame.preprocessingCompleteNs,
                frame == null ? MISSING_LONG : frame.mappingCompleteNs,
                frame == null ? MISSING_LONG : frame.openglHandoffNs,
                render.startNs, endNs,
                raw == null ? -1 : raw.inputPointCount,
                frame == null ? -1 : frame.processedPointCount,
                frame == null ? -1 : frame.queueDepth,
                sourceFrames.getReceived(), processedFrames, sourceFrames.getSkipped(),
                droppedFrames, loggerQueueDrops, processingHz(), fps(),
                raw == null ? Double.NaN : nanosToMillis(frame.mappingCompleteNs - raw.receiveNs),
                raw == null ? Double.NaN : nanosToMillis(endNs - raw.receiveNs),
                frame == null ? Double.NaN : nanosToMillis(endNs - frame.mappingCompleteNs),
                null, null, null, sourceSkippedRate(), renderDroppedRate(),
                droppedSkippedRate());
    }

    private PipelineRow makeSummaryRow(long nowNs) {
        return new PipelineRow(session.sessionId, elapsedSeconds(nowNs), "SESSION_SUMMARY",
                MISSING_LONG, MISSING_LONG, MISSING_LONG, MISSING_LONG, MISSING_LONG,
                MISSING_LONG, MISSING_LONG, MISSING_LONG, MISSING_LONG, -1, -1, -1,
                sourceFrames.getReceived(), processedFrames, sourceFrames.getSkipped(),
                droppedFrames, loggerQueueDrops, processingHz(), fps(),
                Double.NaN, Double.NaN, Double.NaN, processingLatency.snapshot(),
                receiptToRenderLatency.snapshot(), mappedReceiptToRenderLatency.snapshot(),
                sourceSkippedRate(), renderDroppedRate(), droppedSkippedRate());
    }

    private PipelineRow makeDroppedRow(FrameTiming frame, long eventNs) {
        RawFrame raw = frame.raw;
        return new PipelineRow(session.sessionId, elapsedSeconds(eventNs), "RENDER_DROPPED",
                frame.frameId, raw == null ? MISSING_LONG : raw.frameId,
                raw == null ? frame.sensorTimestampNs : raw.sensorTimestampNs,
                raw == null ? MISSING_LONG : raw.receiveNs, frame.preprocessingCompleteNs,
                frame.mappingCompleteNs, frame.openglHandoffNs, MISSING_LONG, MISSING_LONG,
                raw == null ? -1 : raw.inputPointCount, frame.processedPointCount,
                frame.queueDepth, sourceFrames.getReceived(), processedFrames,
                sourceFrames.getSkipped(), droppedFrames, loggerQueueDrops, processingHz(),
                fps(), raw == null ? Double.NaN
                        : nanosToMillis(frame.mappingCompleteNs - raw.receiveNs),
                Double.NaN, Double.NaN, null, null, null, sourceSkippedRate(),
                renderDroppedRate(), droppedSkippedRate());
    }

    private double processingHz() {
        return processedFrames > 1 && lastProcessedNs > firstProcessedNs
                ? (processedFrames - 1) * 1_000_000_000.0 / (lastProcessedNs - firstProcessedNs)
                : Double.NaN;
    }

    private double fps() {
        return renderCount > 1 && lastRenderEndNs > firstRenderEndNs
                ? (renderCount - 1) * 1_000_000_000.0 / (lastRenderEndNs - firstRenderEndNs)
                : Double.NaN;
    }

    private double droppedSkippedRate() {
        long skipped = sourceFrames.getSkipped();
        long expectedSourceFrames = sourceFrames.getReceived() + skipped;
        return expectedSourceFrames == 0 ? Double.NaN
                : (skipped + droppedFrames) / (double) expectedSourceFrames;
    }

    private double sourceSkippedRate() {
        long skipped = sourceFrames.getSkipped();
        long expected = sourceFrames.getReceived() + skipped;
        return expected == 0 ? Double.NaN : skipped / (double) expected;
    }

    private double renderDroppedRate() {
        return handoffFrames == 0 ? Double.NaN : droppedFrames / (double) handoffFrames;
    }

    private double elapsedSeconds(long timestampNs) {
        return (timestampNs - session.startElapsedRealtimeNanos) / 1_000_000_000.0;
    }

    private static double nanosToMillis(long nanos) {
        return nanos / 1_000_000.0;
    }

    private static LinkedHashMap<Long, RawFrame> limitedMap() {
        return new LinkedHashMap<Long, RawFrame>(RAW_HISTORY_LIMIT + 1, 0.75f, false) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Long, RawFrame> eldest) {
                return size() > RAW_HISTORY_LIMIT;
            }
        };
    }

    public static final class FrameTiming {
        final long generation;
        final long frameId;
        final long sensorTimestampNs;
        final long mappingCompleteNs;
        final RawFrame raw;
        volatile long preprocessingCompleteNs = MISSING_LONG;
        volatile long openglHandoffNs = MISSING_LONG;
        volatile long renderStartNs = MISSING_LONG;
        volatile long renderEndNs = MISSING_LONG;
        volatile int processedPointCount = -1;
        volatile int queueDepth = -1;

        FrameTiming(long generation, long frameId, long sensorTimestampNs,
                    long mappingCompleteNs, RawFrame raw) {
            this.generation = generation;
            this.frameId = frameId;
            this.sensorTimestampNs = sensorTimestampNs;
            this.mappingCompleteNs = mappingCompleteNs;
            this.raw = raw;
        }
    }

    public static final class RenderTiming {
        final long generation;
        final FrameTiming frame;
        final long startNs;

        RenderTiming(long generation, FrameTiming frame, long startNs) {
            this.generation = generation;
            this.frame = frame;
            this.startNs = startNs;
        }
    }

    private static final class RawFrame {
        final long frameId;
        final long sensorTimestampNs;
        final long receiveNs;
        final int inputPointCount;

        RawFrame(long frameId, long sensorTimestampNs, long receiveNs, int inputPointCount) {
            this.frameId = frameId;
            this.sensorTimestampNs = sensorTimestampNs;
            this.receiveNs = receiveNs;
            this.inputPointCount = inputPointCount;
        }
    }

    static final class PipelineRow implements AsyncCsvWriter.CsvWritable {
        final String sessionId;
        final double elapsedTime;
        final String event;
        final long[] longs;
        final double[] doubles;
        final LatencyStatistics.Snapshot processingStats;
        final LatencyStatistics.Snapshot receiptStats;
        final LatencyStatistics.Snapshot mappedStats;
        final double sourceSkippedRate;
        final double renderDroppedRate;
        final double droppedRate;

        PipelineRow(String sessionId, double elapsedTime, String event,
                    long frameId, long inputFrameId, long sensorTimestamp, long receive,
                    long preprocessing,
                    long mapping, long handoff, long renderStart, long renderEnd,
                    long inputPoints, long processedPoints, long queueDepth, long receivedFrames,
                    long processedFrames, long skippedFrames, long droppedFrames,
                    long loggerDrops, double processingHz, double fps, double processingLatency,
                    double receiptLatency, double mappedLatency,
                    LatencyStatistics.Snapshot processingStats,
                    LatencyStatistics.Snapshot receiptStats,
                    LatencyStatistics.Snapshot mappedStats, double sourceSkippedRate,
                    double renderDroppedRate, double droppedRate) {
            this.sessionId = sessionId;
            this.elapsedTime = elapsedTime;
            this.event = event;
            this.longs = new long[]{frameId, inputFrameId, sensorTimestamp, receive, preprocessing, mapping,
                    handoff, renderStart, renderEnd, inputPoints, processedPoints, queueDepth,
                    receivedFrames, processedFrames, skippedFrames, droppedFrames, loggerDrops};
            this.doubles = new double[]{processingHz, fps, processingLatency, receiptLatency,
                    mappedLatency};
            this.processingStats = processingStats;
            this.receiptStats = receiptStats;
            this.mappedStats = mappedStats;
            this.sourceSkippedRate = sourceSkippedRate;
            this.renderDroppedRate = renderDroppedRate;
            this.droppedRate = droppedRate;
        }

        @Override
        public void writeCsv(BufferedWriter out) throws IOException {
            out.write(sessionId);
            comma(out, elapsedTime);
            comma(out, event);
            for (int i = 0; i < longs.length; i++) {
                if (i < 9 && longs[i] == MISSING_LONG || i >= 9 && i <= 11 && longs[i] < 0) {
                    comma(out, "");
                } else {
                    comma(out, longs[i]);
                }
            }
            for (double value : doubles) {
                comma(out, value);
            }
            writeStats(out, processingStats);
            writeStats(out, receiptStats);
            writeStats(out, mappedStats);
            comma(out, sourceSkippedRate);
            comma(out, renderDroppedRate);
            comma(out, droppedRate);
        }

        private static void writeStats(BufferedWriter out, LatencyStatistics.Snapshot stats)
                throws IOException {
            comma(out, stats == null ? Double.NaN : stats.mean);
            comma(out, stats == null ? Double.NaN : stats.median);
            comma(out, stats == null ? Double.NaN : stats.p95);
        }

        private static void comma(BufferedWriter out, String value) throws IOException {
            out.write(',');
            out.write(value);
        }

        private static void comma(BufferedWriter out, long value) throws IOException {
            comma(out, Long.toString(value));
        }

        private static void comma(BufferedWriter out, double value) throws IOException {
            comma(out, Double.toString(value));
        }
    }
}
