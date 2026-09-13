package edu.osu.pcv.marslogger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PoseTimestampConverterTest {
    @Test
    public void calibratesLivoxSensorClockFromRawReceiptPairs() {
        long unix = 1_800_000_000_000_000_000L;
        PoseTimestampConverter converter = new PoseTimestampConverter(
                unix, 100_000_000_000L, 110_000_000_000L);
        assertEquals(Long.MIN_VALUE, converter.lidarToUnix(1_020_000_000L));
        for (int i = 0; i < 10; i++) {
            long sensor = 1_000_000_000L + i * 100_000_000L;
            long delay = i == 4 ? 5_000_000L : 30_000_000L;
            assertEquals(i == 9,
                    converter.observeLidarReceipt(sensor, sensor + 109_000_000_000L + delay));
        }
        assertTrue(converter.hasLidarCalibration());
        assertEquals(109_005_000_000L, converter.lidarSensorToBootOffsetNs());
        assertEquals(unix + 25_000_000L,
                converter.lidarToUnix(1_020_000_000L));
        assertEquals(unix + 20_000_000L,
                converter.cameraToUnix(100_020_000_000L, false));
        assertEquals(unix + 20_000_000L,
                converter.cameraToUnix(110_020_000_000L, true));
        assertEquals(Long.MIN_VALUE, converter.lidarToUnix(0));
        assertEquals(Long.MIN_VALUE, converter.lidarToUnix(unix));
        assertFalse(converter.observeLidarReceipt(2_000_000_000L, 2_000_000_000L));
        assertEquals(109_005_000_000L, converter.lidarSensorToBootOffsetNs());
    }

    @Test
    public void recordedSessionClockDomainsCanYieldA20MillisecondPoseMatch() {
        PoseTimestampConverter converter = new PoseTimestampConverter(
                1_789_302_735_347_000_000L, 106_453_180_679_078L,
                118_949_057_056_552L);
        long[] rawSensor = {141_014_909_770L, 141_115_229_770L,
                141_214_109_770L, 141_316_349_770L, 141_415_709_770L,
                141_516_509_770L, 141_616_349_770L, 141_716_669_770L,
                141_816_029_770L, 141_916_349_770L};
        long[] rawHostBoot = {118_949_062_931_083L, 118_949_145_160_510L,
                118_949_248_300_614L, 118_949_359_870_041L, 118_949_453_037_646L,
                118_949_551_861_916L, 118_949_672_544_364L, 118_949_750_450_041L,
                118_949_859_550_406L, 118_949_955_530_093L};
        for (int i = 0; i < rawSensor.length; i++) {
            converter.observeLidarReceipt(rawSensor[i], rawHostBoot[i]);
        }
        long lidarUnix = converter.lidarToUnix(143_416_809_841L);
        long arcoreUnix = converter.cameraToUnix(118_951_439_628_322L, true);
        assertTrue(Math.abs(lidarUnix - arcoreUnix) < 20_000_000L);
    }
}
