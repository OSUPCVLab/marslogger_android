package org.ollide.rosandroid;

/** Paired sensor and Android receipt timestamps for LiDAR clock calibration. */
public interface RawTimestampListener {
    void onRawTimestamp(boolean livox, long sensorTimestampNs, long hostBootReceiveNs);
}
