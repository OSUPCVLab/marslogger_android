package edu.osu.pcv.marslogger.benchmark;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.assertFalse;
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
                temporaryFolder.getRoot(), clock, pipeline, device);

        BenchmarkSession first = manager.start();
        assertSame(first, manager.start());
        assertTrue(manager.isRunning());
        assertTrue(pipeline.isActive());
        assertTrue(device.isActive());

        manager.stop();
        assertFalse(manager.isRunning());
        assertFalse(pipeline.isActive());
        assertFalse(device.isActive());
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
