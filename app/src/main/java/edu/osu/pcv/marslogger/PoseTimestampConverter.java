package edu.osu.pcv.marslogger;

/** A fixed host-clock snapshot for comparing LiDAR and camera poses in Unix nanoseconds. */
final class PoseTimestampConverter {
    private static final long MAX_DISTANCE_FROM_SNAPSHOT_NS = 86_400_000_000_000L;
    private static final int MIN_LIDAR_RECEIPTS = 10;

    // The camera clock is an Android host clock. Livox has its own clock and must be
    // calibrated against the host clock from actual raw-message receipt pairs.
    private final long unixMinusMonotonicNs;
    private final long unixMinusBootNs;
    private final long monotonicAtSnapshotNs;
    private final long bootAtSnapshotNs;
    private long minimumSensorToBootNs;
    private long lidarSensorAtCalibrationNs;
    private int lidarReceipts;

    PoseTimestampConverter(long unixAtSnapshotNs, long monotonicAtSnapshotNs,
            long bootAtSnapshotNs) {
        this.unixMinusMonotonicNs = unixAtSnapshotNs - monotonicAtSnapshotNs;
        this.unixMinusBootNs = unixAtSnapshotNs - bootAtSnapshotNs;
        this.monotonicAtSnapshotNs = monotonicAtSnapshotNs;
        this.bootAtSnapshotNs = bootAtSnapshotNs;
    }

    /**
     * The smallest observed receive-minus-sensor difference removes most queueing delay.
     * Freeze it after ten scans so the fixed world alignment uses one clock mapping.
     */
    synchronized boolean observeLidarReceipt(long sensorTimestampNs, long hostBootReceiveNs) {
        if (lidarReceipts >= MIN_LIDAR_RECEIPTS
                || sensorTimestampNs <= 0 || hostBootReceiveNs <= 0) return false;
        long candidate;
        try {
            candidate = Math.subtractExact(hostBootReceiveNs, sensorTimestampNs);
        } catch (ArithmeticException error) {
            return false;
        }
        if (lidarReceipts == 0 || candidate < minimumSensorToBootNs) {
            minimumSensorToBootNs = candidate;
        }
        lidarSensorAtCalibrationNs = sensorTimestampNs;
        lidarReceipts++;
        return lidarReceipts == MIN_LIDAR_RECEIPTS;
    }

    synchronized long lidarToUnix(long lidarSensorTimestampNs) {
        if (lidarReceipts < MIN_LIDAR_RECEIPTS || lidarSensorTimestampNs <= 0
                || Math.abs((double) lidarSensorTimestampNs - lidarSensorAtCalibrationNs)
                > MAX_DISTANCE_FROM_SNAPSHOT_NS) return Long.MIN_VALUE;
        try {
            long hostBootNs = Math.addExact(lidarSensorTimestampNs, minimumSensorToBootNs);
            return Math.addExact(hostBootNs, unixMinusBootNs);
        } catch (ArithmeticException error) {
            return Long.MIN_VALUE;
        }
    }

    synchronized boolean hasLidarCalibration() {
        return lidarReceipts >= MIN_LIDAR_RECEIPTS;
    }

    synchronized long lidarSensorToBootOffsetNs() {
        return minimumSensorToBootNs;
    }

    /** ARCore's Android-camera timestamp has the Camera2 sensor timestamp clock. */
    long cameraToUnix(long cameraTimestampNs, boolean cameraUsesBootTime) {
        return cameraUsesBootTime
                ? shift(cameraTimestampNs, bootAtSnapshotNs, unixMinusBootNs)
                : shift(cameraTimestampNs, monotonicAtSnapshotNs, unixMinusMonotonicNs);
    }

    private static long shift(long timestampNs, long referenceNs, long offsetNs) {
        if (timestampNs <= 0
                || Math.abs((double) timestampNs - referenceNs) > MAX_DISTANCE_FROM_SNAPSHOT_NS) {
            return Long.MIN_VALUE;
        }
        try {
            return Math.addExact(timestampNs, offsetNs);
        } catch (ArithmeticException error) {
            return Long.MIN_VALUE;
        }
    }
}
