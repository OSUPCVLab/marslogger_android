package edu.osu.pcv.marslogger.benchmark;

import android.content.Context;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;

/** Owns the shared identity and monotonic origin for both benchmark CSV streams. */
public final class BenchmarkSessionManager {
    private static volatile BenchmarkSessionManager instance;

    private final File outputDirectory;
    private final MonotonicClock clock;
    private final PipelinePerformanceLogger pipelineLogger;
    private final DeviceStatsLogger deviceStatsLogger;
    private BenchmarkSession activeSession;
    private BenchmarkSession lastSession;

    public static BenchmarkSessionManager getInstance(Context context) {
        if (instance == null) {
            synchronized (BenchmarkSessionManager.class) {
                if (instance == null) {
                    Context appContext = context.getApplicationContext();
                    File externalFiles = appContext.getExternalFilesDir(null);
                    File base = new File(externalFiles == null ? appContext.getFilesDir()
                            : externalFiles, "benchmarks");
                    instance = new BenchmarkSessionManager(base, MonotonicClock.SYSTEM,
                            new PipelinePerformanceLogger(), new DeviceStatsLogger(appContext));
                }
            }
        }
        return instance;
    }

    BenchmarkSessionManager(File outputDirectory, MonotonicClock clock,
                            PipelinePerformanceLogger pipelineLogger,
                            DeviceStatsLogger deviceStatsLogger) {
        this.outputDirectory = outputDirectory;
        this.clock = clock;
        this.pipelineLogger = pipelineLogger;
        this.deviceStatsLogger = deviceStatsLogger;
    }

    public synchronized BenchmarkSession start() {
        if (activeSession != null) {
            return activeSession;
        }
        String sessionId = new SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US)
                .format(new Date()) + "_" + UUID.randomUUID().toString().substring(0, 8);
        long startNs = clock.nowNanos();
        BenchmarkSession session = new BenchmarkSession(sessionId, startNs,
                new File(outputDirectory, "pipeline_perf_" + sessionId + ".csv"),
                new File(outputDirectory, "device_stats_" + sessionId + ".csv"));
        activeSession = session;
        lastSession = session;
        pipelineLogger.start(session);
        deviceStatsLogger.start(session);
        return session;
    }

    public synchronized void stop() {
        if (activeSession == null) {
            return;
        }
        activeSession = null;
        pipelineLogger.stop();
        deviceStatsLogger.stop();
    }

    public synchronized boolean isRunning() {
        return activeSession != null;
    }

    public synchronized BenchmarkSession getActiveSession() {
        return activeSession;
    }

    public synchronized BenchmarkSession getLastSession() {
        return lastSession;
    }

    public PipelinePerformanceLogger getPipelineLogger() {
        return pipelineLogger;
    }
}
