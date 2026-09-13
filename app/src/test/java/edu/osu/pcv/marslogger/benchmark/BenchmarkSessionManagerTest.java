package edu.osu.pcv.marslogger.benchmark;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class BenchmarkSessionManagerTest {
    @Rule public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void startsAndStopsBothLoggersAsOneSession() throws Exception {
        PipelinePerformanceLoggerTest.FakeClock clock =
                new PipelinePerformanceLoggerTest.FakeClock(5_000_000_000L);
        PipelinePerformanceLogger pipeline = new PipelinePerformanceLogger(clock);
        DeviceStatsLogger device = new DeviceStatsLogger(clock, new UnavailableMetrics());
        BenchmarkSessionManager manager = new BenchmarkSessionManager(
                clock, pipeline, device);
        java.io.File recordingDirectory = temporaryFolder.newFolder("recording");

        BenchmarkSession first = manager.start(recordingDirectory);
        assertSame(first, manager.start(recordingDirectory));
        assertEquals(new java.io.File(recordingDirectory, "benchmark"),
                first.pipelineFile.getParentFile());
        assertEquals(new java.io.File(recordingDirectory, "benchmark"),
                first.deviceStatsFile.getParentFile());
        assertTrue(manager.isRunning());
        assertTrue(pipeline.isActive());
        assertTrue(device.isActive());

        manager.stop();
        assertFalse(manager.isRunning());
        assertFalse(pipeline.isActive());
        assertFalse(device.isActive());
        assertTrue(pipeline.awaitStopped(2000));
        assertTrue(device.awaitStopped(2000));
        assertTrue(first.pipelineFile.isFile());
        assertTrue(first.deviceStatsFile.isFile());

        java.io.File nextRecording = temporaryFolder.newFolder("next_recording");
        BenchmarkSession second = manager.start(nextRecording);
        assertNotSame(first, second);
        assertEquals(new java.io.File(nextRecording, "benchmark"),
                second.pipelineFile.getParentFile());
        manager.stop();
        assertTrue(pipeline.awaitStopped(2000));
        assertTrue(device.awaitStopped(2000));
    }

    private static final class UnavailableMetrics implements DeviceStatsLogger.MetricsProvider {
        @Override
        public DeviceStatsLogger.DeviceSample sample(long timestampNs) {
            return DeviceStatsLogger.DeviceSample.unavailable(timestampNs);
        }

        @Override
        public void reset() {
        }
    }
}
