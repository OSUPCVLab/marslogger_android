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

    /** Registered scans already use the mapping frame. */
    public void addWorldPoint(double wx, double wy, double wz) {
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

}
