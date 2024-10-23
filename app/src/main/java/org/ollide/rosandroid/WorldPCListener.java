package org.ollide.rosandroid;

import sensor_msgs.PointCloud2;

public interface WorldPCListener {
    void onWorldPC(PointCloud2 msg);
}
