package edu.osu.pcv.marslogger.benchmark;

import org.junit.Test;

import java.io.BufferedWriter;
import java.io.StringWriter;

import static org.junit.Assert.assertTrue;

public class DeviceStatsLoggerTest {
    @Test
    public void unavailableMetricsStayNaNOrBlankInCsv() throws Exception {
        DeviceStatsLogger.DeviceSample sample = DeviceStatsLogger.DeviceSample.unavailable(42);
        StringWriter stringWriter = new StringWriter();
        BufferedWriter writer = new BufferedWriter(stringWriter);
        new DeviceStatsLogger.DeviceRow("session", 1.0, sample).writeCsv(writer);
        writer.flush();

        String csv = stringWriter.toString();
        assertTrue(Double.isNaN(sample.processCpuPercent));
        assertTrue(Double.isNaN(sample.batteryCurrentMa));
        assertTrue(csv.contains(",NaN,NaN,NaN,"));
        assertTrue(csv.endsWith(",NaN,"));
    }
}
