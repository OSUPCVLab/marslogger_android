package org.ollide.rosandroid;

import sensor_msgs.PointCloud2;

/** Receives raw LiDAR scans for the visualization-only global voxel map. */
public interface RawPCListener {
    void onRawPC(PointCloud2 msg);
}
