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

        alignment.addLidar(1_000_000_000L, 1_000_000_000L, lidarAtMatch);
        alignment.addCamera(1, 1_020_000_000L, 1_020_000_000L, cameraAtMatch);

        assertTrue(alignment.isAligned());
        assertEquals(1, collector.matches.size());
        assertEquals(20_000_000L, collector.matches.get(0).differenceNs);
        assertTransformEquals(lidarAtMatch, collector.transformed.get(0));

        ArCoreLidarAlignment.RigidTransform cameraMoved = cameraAtMatch.multiply(
                new ArCoreLidarAlignment.RigidTransform(0.5, 0, 0, 0, 0, 0, 1));
        alignment.addCamera(1, 1_120_000_000L, 1_120_000_000L, cameraMoved);
        ArCoreLidarAlignment.RigidTransform expected = lidarAtMatch
                .multiply(ArCoreLidarAlignment.L_T_C)
                .multiply(cameraAtMatch.inverse())
                .multiply(cameraMoved)
                .multiply(ArCoreLidarAlignment.L_T_C.inverse());
        assertTransformEquals(expected, collector.transformed.get(1));

        alignment.addLidar(1_120_000_000L, 1_120_000_000L,
                new ArCoreLidarAlignment.RigidTransform(99, 99, 99, 0, 0, 0, 1));
        assertEquals(1, collector.matches.size());

        alignment.addCamera(2, 1_220_000_000L, 1_220_000_000L, cameraMoved);
        assertFalse(alignment.isAligned());
        assertEquals(1, collector.resets);
        alignment.addLidar(1_230_000_000L, 1_230_000_000L, lidarAtMatch);
        assertTrue(alignment.isAligned());
        assertEquals(2, collector.matches.size());
        assertTrue(collector.matches.get(1).generation > collector.matches.get(0).generation);
    }

    @Test
    public void refusesDistantPairAndUnknownClock() {
        Collector collector = new Collector();
        ArCoreLidarAlignment alignment = new ArCoreLidarAlignment(collector);
        ArCoreLidarAlignment.RigidTransform identity =
                new ArCoreLidarAlignment.RigidTransform(0, 0, 0, 0, 0, 0, 1);
        alignment.addLidar(1_000_000_000L, 1_000_000_000L, identity);
        alignment.addCamera(1, 1_200_000_000L, 1_200_000_000L, identity);
        assertFalse(alignment.isAligned());
        alignment.addLidar(1_220_000_000L, 1_220_000_000L, identity);
        assertTrue(alignment.isAligned());
        assertNotNull(collector.matches.get(0).worldLidarFromWorldCamera);

        long bootNow = 100_000_000_000L;
        long monoNow = 90_000_000_000L;
        long unixNow = 1_800_000_000_000_000_000L;
        long expected = bootNow - 100_000_000L;
        assertEquals(expected, ArCoreLidarAlignment.normalizeToBootTime(
                expected, bootNow, monoNow, unixNow));
        assertEquals(expected, ArCoreLidarAlignment.normalizeToBootTime(
                monoNow - 100_000_000L, bootNow, monoNow, unixNow));
        assertEquals(expected, ArCoreLidarAlignment.normalizeToBootTime(
                unixNow - 100_000_000L, bootNow, monoNow, unixNow));
        assertEquals(Long.MIN_VALUE, ArCoreLidarAlignment.normalizeToBootTime(
                50_000_000_000L, bootNow, monoNow, unixNow));
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
