package org.ollide.rosandroid;

public interface LocationUpdateListener {
    void onLocationUpdate(long timestampNs, double x, double y, double z,
                          double qx, double qy, double qz, double qw);

}
