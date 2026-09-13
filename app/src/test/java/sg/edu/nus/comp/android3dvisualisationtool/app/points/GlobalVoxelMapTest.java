package sg.edu.nus.comp.android3dvisualisationtool.app.points;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class GlobalVoxelMapTest {
    private static final GlobalVoxelMap.Pose IDENTITY =
            new GlobalVoxelMap.Pose(0, 0, 0, 0, 0, 0, 1);

    @Test
    public void accumulatesCentroidAndCountWithoutRetainingPoints() {
        GlobalVoxelMap map = new GlobalVoxelMap();
        map.addPoint(0.02f, 0.03f, 0.04f, IDENTITY);
        map.addPoint(0.10f, 0.05f, 0.12f, IDENTITY);

        assertEquals(1, map.size());
        float[] centroid = map.snapshotCentroids();
        assertEquals(0.06f, centroid[0], 1e-6);
        assertEquals(0.04f, centroid[1], 1e-6);
        assertEquals(0.08f, centroid[2], 1e-6);
    }

    @Test
    public void negativeCoordinatesUseFloorAndPoseRotatesIntoWorld() {
        GlobalVoxelMap map = new GlobalVoxelMap();
        map.addPoint(-0.01f, 0f, 0f, IDENTITY);
        map.addPoint(0.01f, 0f, 0f, IDENTITY);
        assertEquals(2, map.size());

        map.clear();
        map.addPoint(0.99f, 0f, 0f, IDENTITY);
        map.addPoint(1.00f, 0f, 0f, IDENTITY);
        assertEquals(2, map.size());

        map.clear();
        // 90 degrees about Z: local +X becomes world +Y, then add translation.
        double half = Math.sqrt(0.5);
        GlobalVoxelMap.Pose pose = new GlobalVoxelMap.Pose(1, 2, 3, 0, 0, half, half);
        map.addPoint(1f, 0f, 0f, pose);
        float[] xyz = map.snapshotCentroids();
        assertEquals(1f, xyz[0], 1e-6);
        assertEquals(3f, xyz[1], 1e-6);
        assertEquals(3f, xyz[2], 1e-6);
    }
}
