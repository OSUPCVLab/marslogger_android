package sg.edu.nus.comp.android3dvisualisationtool.app.points;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class GlobalVoxelMapTest {
    @Test
    public void accumulatesCentroidAndCountWithoutRetainingPoints() {
        GlobalVoxelMap map = new GlobalVoxelMap();
        map.addWorldPoint(0.02, 0.03, 0.04);
        map.addWorldPoint(0.10, 0.05, 0.12);

        assertEquals(1, map.size());
        float[] centroid = map.snapshotCentroids();
        assertEquals(0.06f, centroid[0], 1e-6);
        assertEquals(0.04f, centroid[1], 1e-6);
        assertEquals(0.08f, centroid[2], 1e-6);
    }

    @Test
    public void registeredCoordinatesUseWorldVoxelsWithoutAnotherTransform() {
        GlobalVoxelMap map = new GlobalVoxelMap();
        map.addWorldPoint(-0.01, 0, 0);
        map.addWorldPoint(0.01, 0, 0);
        assertEquals(2, map.size());

        map.clear();
        map.addWorldPoint(0.99, 0, 0);
        map.addWorldPoint(1.00, 0, 0);
        assertEquals(2, map.size());

        map.clear();
        map.addWorldPoint(1, 2, 3);
        float[] xyz = map.snapshotCentroids();
        assertEquals(1f, xyz[0], 1e-6);
        assertEquals(2f, xyz[1], 1e-6);
        assertEquals(3f, xyz[2], 1e-6);
    }
}
