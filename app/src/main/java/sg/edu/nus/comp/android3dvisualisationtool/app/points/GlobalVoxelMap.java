package sg.edu.nus.comp.android3dvisualisationtool.app.points;

import java.util.HashMap;
import java.util.Map;

/** Visualization-only 20 cm voxel map. Only a centroid and count survive each scan. */
public final class GlobalVoxelMap {
    public static final double VOXEL_SIZE_M = 0.20;

    private final Map<VoxelKey, VoxelData> globalMap = new HashMap<>();

    public void clear() {
        globalMap.clear();
    }

    public int size() {
        return globalMap.size();
    }

    public void addPoint(float x, float y, float z, Pose pose) {
        if (pose == null || !Float.isFinite(x) || !Float.isFinite(y)
                || !Float.isFinite(z)) return;
        double wx = pose.tx + pose.r00 * x + pose.r01 * y + pose.r02 * z;
        double wy = pose.ty + pose.r10 * x + pose.r11 * y + pose.r12 * z;
        double wz = pose.tz + pose.r20 * x + pose.r21 * y + pose.r22 * z;
        if (!Double.isFinite(wx) || !Double.isFinite(wy) || !Double.isFinite(wz)) return;
        VoxelKey key = new VoxelKey((int) Math.floor(wx / VOXEL_SIZE_M),
                (int) Math.floor(wy / VOXEL_SIZE_M),
                (int) Math.floor(wz / VOXEL_SIZE_M));
        VoxelData voxel = globalMap.get(key);
        if (voxel == null) {
            globalMap.put(key, new VoxelData(wx, wy, wz));
        } else {
            voxel.add(wx, wy, wz);
        }
    }

    /** Flat XYZ array of world-frame centroids for rendering. */
    public float[] snapshotCentroids() {
        float[] xyz = new float[globalMap.size() * 3];
        int index = 0;
        for (VoxelData voxel : globalMap.values()) {
            xyz[index++] = (float) voxel.x;
            xyz[index++] = (float) voxel.y;
            xyz[index++] = (float) voxel.z;
        }
        return xyz;
    }

    static final class VoxelKey {
        final int ix, iy, iz;

        VoxelKey(int ix, int iy, int iz) {
            this.ix = ix;
            this.iy = iy;
            this.iz = iz;
        }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof VoxelKey)) return false;
            VoxelKey key = (VoxelKey) other;
            return ix == key.ix && iy == key.iy && iz == key.iz;
        }

        @Override public int hashCode() {
            int hash = 31 * ix + iy;
            return 31 * hash + iz;
        }
    }

    static final class VoxelData {
        double x, y, z;
        long count;

        VoxelData(double x, double y, double z) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.count = 1;
        }

        void add(double nx, double ny, double nz) {
            double next = count + 1.0;
            x = (x * count + nx) / next;
            y = (y * count + ny) / next;
            z = (z * count + nz) / next;
            count++;
        }
    }

    /** Current Wl_T_L pose, with quaternion normalized once per odometry message. */
    public static final class Pose {
        final double tx, ty, tz;
        final double r00, r01, r02, r10, r11, r12, r20, r21, r22;

        public Pose(double tx, double ty, double tz,
                double qx, double qy, double qz, double qw) {
            double norm = Math.sqrt(qx * qx + qy * qy + qz * qz + qw * qw);
            if (!Double.isFinite(norm) || norm < 1e-12
                    || !Double.isFinite(tx) || !Double.isFinite(ty) || !Double.isFinite(tz)) {
                throw new IllegalArgumentException("Invalid LiDAR pose");
            }
            qx /= norm; qy /= norm; qz /= norm; qw /= norm;
            this.tx = tx; this.ty = ty; this.tz = tz;
            r00 = 1 - 2 * (qy * qy + qz * qz);
            r01 = 2 * (qx * qy - qz * qw);
            r02 = 2 * (qx * qz + qy * qw);
            r10 = 2 * (qx * qy + qz * qw);
            r11 = 1 - 2 * (qx * qx + qz * qz);
            r12 = 2 * (qy * qz - qx * qw);
            r20 = 2 * (qx * qz - qy * qw);
            r21 = 2 * (qy * qz + qx * qw);
            r22 = 1 - 2 * (qx * qx + qy * qy);
        }
    }
}
