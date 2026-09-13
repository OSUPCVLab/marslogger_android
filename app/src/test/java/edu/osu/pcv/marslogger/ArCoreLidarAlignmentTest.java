package edu.osu.pcv.marslogger;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class ArCoreLidarAlignmentTest {
    private static final double EPSILON = 1e-8;

    @Test
    public void matchesOnceAndPlacesCameraDerivedLidarAtLidarPose() {
        Collector collector = new Collector();
        ArCoreLidarAlignment alignment = new ArCoreLidarAlignment(collector);
        ArCoreLidarAlignment.RigidTransform lidarAtMatch =
                new ArCoreLidarAlignment.RigidTransform(1, 2, 3, 0, 0, 0.70710678, 0.70710678);
        ArCoreLidarAlignment.RigidTransform cameraAtMatch =
                new ArCoreLidarAlignment.RigidTransform(4, -1, 2, 0, 0.38268343, 0, 0.92387953);

        alignment.addLidar(1_019_000_000L, 1_019_000_000L, lidarAtMatch);
        alignment.addCamera(1, 1_020_000_000L, 1_020_000_000L, cameraAtMatch);
        // Wait until the LiDAR timeline passes the camera stamp plus the 20 ms limit.
        alignment.addLidar(1_041_000_000L, 1_041_000_000L, lidarAtMatch);

        assertTrue(alignment.isAligned());
        assertEquals(1, collector.matches.size());
        assertEquals(1_000_000L, collector.matches.get(0).differenceNs);
        assertEquals(1, collector.matches.get(0).matchedPosePairs);
        assertTransformEquals(lidarAtMatch, collector.transformed.get(0));

        ArCoreLidarAlignment.RigidTransform cameraMoved = cameraAtMatch.multiply(
                new ArCoreLidarAlignment.RigidTransform(0.5, 0, 0, 0, 0, 0, 1));
        alignment.addCamera(1, 1_120_000_000L, 1_120_000_000L, cameraMoved);
        ArCoreLidarAlignment.RigidTransform expected = lidarAtMatch
                .multiply(ArCoreLidarAlignment.L_T_C_ARCORE)
                .multiply(cameraAtMatch.inverse())
                .multiply(cameraMoved)
                .multiply(ArCoreLidarAlignment.L_T_C_ARCORE.inverse());
        assertTransformEquals(expected, collector.transformed.get(1));

        alignment.addLidar(1_120_000_000L, 1_120_000_000L,
                new ArCoreLidarAlignment.RigidTransform(99, 99, 99, 0, 0, 0, 1));
        alignment.addLidar(1_141_000_000L, 1_141_000_000L,
                new ArCoreLidarAlignment.RigidTransform(99, 99, 99, 0, 0, 0, 1));
        assertEquals(1, collector.matches.size());
        assertEquals(2, alignment.getMatchedPosePairs());

        alignment.addCamera(2, 1_220_000_000L, 1_220_000_000L, cameraMoved);
        assertFalse(alignment.isAligned());
        assertEquals(1, collector.resets);
        alignment.addLidar(1_230_000_000L, 1_230_000_000L, lidarAtMatch);
        alignment.addLidar(1_241_000_000L, 1_241_000_000L, lidarAtMatch);
        assertTrue(alignment.isAligned());
        assertEquals(2, collector.matches.size());
        assertTrue(collector.matches.get(1).generation > collector.matches.get(0).generation);
    }

    @Test
    public void refusesDistantPairAndUsesNearestMaturedLidar() {
        Collector collector = new Collector();
        ArCoreLidarAlignment alignment = new ArCoreLidarAlignment(collector);
        ArCoreLidarAlignment.RigidTransform identity =
                new ArCoreLidarAlignment.RigidTransform(0, 0, 0, 0, 0, 0, 1);
        alignment.addLidar(1_000_000_000L, 1_000_000_000L, identity);
        alignment.addCamera(1, 1_200_000_000L, 1_200_000_000L, identity);
        assertFalse(alignment.isAligned());
        alignment.addLidar(1_300_000_000L, 1_300_000_000L, identity);
        assertFalse(alignment.isAligned());
        alignment.addLidar(1_382_000_000L, 1_382_000_000L, identity);
        alignment.addCamera(1, 1_400_000_000L, 1_400_000_000L, identity);
        alignment.addLidar(1_402_000_000L, 1_402_000_000L, identity);
        assertFalse(alignment.isAligned());
        alignment.addLidar(1_421_000_000L, 1_421_000_000L, identity);
        assertTrue(alignment.isAligned());
        assertNotNull(collector.matches.get(0).worldLidarFromWorldCamera);
        assertEquals(2_000_000L, collector.matches.get(0).differenceNs);
        assertEquals(1, alignment.getMatchedPosePairs());
    }

    @Test
    public void configurableMaximumRejectsTooDistantNearestPose() {
        Collector collector = new Collector();
        ArCoreLidarAlignment alignment = new ArCoreLidarAlignment(collector, 5_000_000L);
        ArCoreLidarAlignment.RigidTransform identity =
                new ArCoreLidarAlignment.RigidTransform(0, 0, 0, 0, 0, 0, 1);
        alignment.addLidar(1_000_000_000L, 1_000_000_000L, identity);
        alignment.addCamera(1, 1_006_000_000L, 1_006_000_000L, identity);
        alignment.addLidar(1_012_000_000L, 1_012_000_000L, identity);
        assertFalse(alignment.isAligned());
        alignment.addCamera(1, 1_020_000_000L, 1_020_000_000L, identity);
        alignment.addLidar(1_024_000_000L, 1_024_000_000L, identity);
        alignment.addLidar(1_026_000_000L, 1_026_000_000L, identity);
        assertTrue(alignment.isAligned());
        assertEquals(4_000_000L, collector.matches.get(0).differenceNs);
    }

    @Test
    public void composesRotationAndInverseInTheExpectedDirection() {
        ArCoreLidarAlignment.RigidTransform quarterTurn =
                new ArCoreLidarAlignment.RigidTransform(0, 0, 0,
                        0, 0, Math.sqrt(0.5), Math.sqrt(0.5));
        ArCoreLidarAlignment.RigidTransform stepAlongX =
                new ArCoreLidarAlignment.RigidTransform(1, 0, 0, 0, 0, 0, 1);
        ArCoreLidarAlignment.RigidTransform result = quarterTurn.multiply(stepAlongX);
        assertEquals(0, result.x, EPSILON);
        assertEquals(1, result.y, EPSILON);
        assertEquals(0, result.z, EPSILON);
        assertTransformEquals(stepAlongX, quarterTurn.inverse().multiply(result));
    }

    @Test
    public void convertsArCoreOpenGlCameraAxesToCadOpticalAxes() {
        double[] frame = ArCoreLidarAlignment.ARCORE_T_OPTICAL.matrixRowMajor();
        assertEquals(1, frame[0], EPSILON);
        assertEquals(-1, frame[5], EPSILON);
        assertEquals(-1, frame[10], EPSILON);
        assertEquals(1, frame[15], EPSILON);
        assertTransformEquals(ArCoreLidarAlignment.L_T_C,
                ArCoreLidarAlignment.L_T_C_ARCORE
                        .multiply(ArCoreLidarAlignment.ARCORE_T_OPTICAL));

        Collector collector = new Collector();
        ArCoreLidarAlignment alignment = new ArCoreLidarAlignment(collector);
        ArCoreLidarAlignment.RigidTransform lidarAtStart =
                new ArCoreLidarAlignment.RigidTransform(0, 0, 0, 0, 0, 0, 1);
        ArCoreLidarAlignment.RigidTransform lidarMoved =
                new ArCoreLidarAlignment.RigidTransform(0.7, -0.3, 0.2,
                        0, 0, Math.sqrt(0.5), Math.sqrt(0.5));
        ArCoreLidarAlignment.RigidTransform cameraAtStart =
                lidarAtStart.multiply(ArCoreLidarAlignment.L_T_C_ARCORE);
        ArCoreLidarAlignment.RigidTransform cameraMoved =
                lidarMoved.multiply(ArCoreLidarAlignment.L_T_C_ARCORE);
        alignment.addLidar(1_000_000_000L, 1_000_000_000L, lidarAtStart);
        alignment.addCamera(1, 1_001_000_000L, 1_001_000_000L, cameraAtStart);
        alignment.addLidar(1_022_000_000L, 1_022_000_000L, lidarAtStart);
        alignment.addCamera(1, 1_100_000_000L, 1_100_000_000L, cameraMoved);
        assertTransformEquals(lidarAtStart, collector.transformed.get(0));
        assertTransformEquals(lidarMoved, collector.transformed.get(1));
    }

    private static void assertTransformEquals(ArCoreLidarAlignment.RigidTransform expected,
                                              ArCoreLidarAlignment.RigidTransform actual) {
        double[] left = expected.matrixRowMajor();
        double[] right = actual.matrixRowMajor();
        for (int i = 0; i < left.length; i++) {
            assertEquals("matrix element " + i, left[i], right[i], EPSILON);
        }
    }

    private static final class Collector implements ArCoreLidarAlignment.Listener {
        final List<ArCoreLidarAlignment.Match> matches = new ArrayList<>();
        final List<ArCoreLidarAlignment.RigidTransform> transformed = new ArrayList<>();
        int resets;

        @Override
        public void onAlignmentReset(long generation) {
            resets++;
        }

        @Override
        public void onAligned(ArCoreLidarAlignment.Match match) {
            matches.add(match);
        }

        @Override
        public void onTransformedPose(long generation, int originId, long cameraTimestampNs,
                ArCoreLidarAlignment.RigidTransform pose) {
            transformed.add(pose);
        }
    }
}
