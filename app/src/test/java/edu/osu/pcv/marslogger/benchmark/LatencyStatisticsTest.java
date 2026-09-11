package edu.osu.pcv.marslogger.benchmark;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class LatencyStatisticsTest {
    @Test
    public void calculatesMeanMedianAndP95() {
        LatencyStatistics statistics = new LatencyStatistics();
        for (int value = 1; value <= 5; value++) {
            statistics.add(value);
        }

        LatencyStatistics.Snapshot result = statistics.snapshot();
        assertEquals(5, result.count);
        assertEquals(3.0, result.mean, 0.0001);
        assertEquals(3.0, result.median, 0.0001);
        assertEquals(4.8, result.p95, 0.0001);
    }

    @Test
    public void emptyStatisticsAreUnavailable() {
        LatencyStatistics.Snapshot result = new LatencyStatistics().snapshot();
        assertEquals(0, result.count);
        assertTrue(Double.isNaN(result.mean));
        assertTrue(Double.isNaN(result.median));
        assertTrue(Double.isNaN(result.p95));
    }
}
