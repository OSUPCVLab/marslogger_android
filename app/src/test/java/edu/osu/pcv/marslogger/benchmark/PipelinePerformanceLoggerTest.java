package edu.osu.pcv.marslogger.benchmark;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class PipelinePerformanceLoggerTest {
    @Rule public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void writesFrameAndSummaryCsvWithoutSynchronousIo() throws Exception {
        FakeClock clock = new FakeClock(1_000_000_000L);
        File pipelineFile = temporaryFolder.newFile("pipeline.csv");
        File deviceFile = temporaryFolder.newFile("device.csv");
        BenchmarkSession session = new BenchmarkSession("test_session", clock.nowNanos(),
                pipelineFile, deviceFile);
        PipelinePerformanceLogger logger = new PipelinePerformanceLogger(clock);
        logger.start(session);

        clock.advanceMs(10);
        logger.onRawFrameReceived(7, 1234, 100);
        clock.advanceMs(20);
        PipelinePerformanceLogger.FrameTiming frame =
                logger.onMappingOutputReceived(7, 1234);
        clock.advanceMs(2);
        logger.onPreprocessingComplete(frame, 80);
        clock.advanceMs(3);
        logger.onOpenGlHandoff(frame);
        clock.advanceMs(4);
        PipelinePerformanceLogger.RenderTiming render = logger.onRenderStart();
        clock.advanceMs(5);
        logger.onRenderEnd(render);
        logger.stop();

        assertTrue(logger.awaitStopped(2000));
        List<String> lines = Files.readAllLines(pipelineFile.toPath(), StandardCharsets.UTF_8);
        assertEquals(PipelinePerformanceLogger.CSV_HEADER, lines.get(0));
        assertTrue(lines.get(1).contains("test_session"));
        assertTrue(lines.get(1).contains(",RAW_RECEIVE,,7,1234,"));
        assertTrue(lines.get(2).contains(",RENDER,7,7,1234,"));
        assertTrue(lines.get(lines.size() - 1).contains(",SESSION_SUMMARY,"));
        assertEquals(PipelinePerformanceLogger.CSV_HEADER.split(",", -1).length,
                lines.get(2).split(",", -1).length);
    }

    @Test
    public void accountsForSourceGapsAndCoalescedRenderFrames() throws Exception {
        FakeClock clock = new FakeClock(1_000_000_000L);
        PipelinePerformanceLogger logger = new PipelinePerformanceLogger(clock);
        logger.start(new BenchmarkSession("drops", clock.nowNanos(),
                temporaryFolder.newFile("drops.csv"), temporaryFolder.newFile("unused.csv")));

        logger.onRawFrameReceived(1, 101, 10);
        logger.onOpenGlHandoff(logger.onMappingOutputReceived(1, 101));
        logger.onRawFrameReceived(4, 104, 10);
        logger.onOpenGlHandoff(logger.onMappingOutputReceived(4, 104));

        assertEquals(2, logger.getSkippedFramesForTest());
        assertEquals(1, logger.getDroppedFramesForTest());
        logger.stop();
        assertTrue(logger.awaitStopped(2000));
    }

    static final class FakeClock implements MonotonicClock {
        private long now;

        FakeClock(long now) {
            this.now = now;
        }

        void advanceMs(long milliseconds) {
            now += milliseconds * 1_000_000L;
        }

        @Override
        public long nowNanos() {
            return now;
        }
    }
}
