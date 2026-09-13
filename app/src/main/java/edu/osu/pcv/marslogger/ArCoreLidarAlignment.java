package edu.osu.pcv.marslogger;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/** One-time alignment of ARCore camera poses to the LiDAR mapping world. */
final class ArCoreLidarAlignment {
    static final long DEFAULT_MAX_MATCH_DELTA_NS = 20_000_000L;
    private static final int MAX_PENDING_POSES = 64;

    // CAD camera is optical: +X right, +Y down, +Z toward the scene.
    // T_lidar_camera = [tx, ty, tz, qx, qy, qz, qw], in metres.
    static final RigidTransform L_T_C = new RigidTransform(
            0.09165818567908458, 0.024371359183858687, -0.07761523102914086,
            -0.006557708581064656, -0.5943650948131397,
            0.8041392764010252, 0.006866926180545729);
    // ARCore Camera.getPose() is OpenGL: +X right, +Y up, -Z toward the scene.
    // This is a proper 180-degree rotation about X, not an X/Y permutation.
    static final RigidTransform ARCORE_T_OPTICAL = new RigidTransform(
            0, 0, 0, 1, 0, 0, 0);
    static final RigidTransform L_T_C_ARCORE = L_T_C.multiply(ARCORE_T_OPTICAL.inverse());
    private static final RigidTransform C_ARCORE_T_L = L_T_C_ARCORE.inverse();

    interface Listener {
        void onAlignmentReset(long generation);
        void onAligned(Match match);
        void onTransformedPose(long generation, int originId, long cameraTimestampNs,
                               RigidTransform worldLidarFromLidar);
    }

    static final class Match {
        final long generation;
        final int originId;
        final long lidarTimestampNs;
        final long cameraTimestampNs;
        final long lidarUnixTimestampNs;
        final long cameraUnixTimestampNs;
        final long differenceNs;
        final long matchedPosePairs;
        final RigidTransform worldLidarFromWorldCamera;

        Match(long generation, int originId, StampedPose lidar, StampedPose camera,
              RigidTransform worldLidarFromWorldCamera, long matchedPosePairs) {
            this.generation = generation;
            this.originId = originId;
            lidarTimestampNs = lidar.timestampNs;
            cameraTimestampNs = camera.timestampNs;
            lidarUnixTimestampNs = lidar.unixTimestampNs;
            cameraUnixTimestampNs = camera.unixTimestampNs;
            differenceNs = Math.abs(lidar.unixTimestampNs - camera.unixTimestampNs);
            this.matchedPosePairs = matchedPosePairs;
            this.worldLidarFromWorldCamera = worldLidarFromWorldCamera;
        }
    }

    private static final class StampedPose {
        final long timestampNs;
        final long unixTimestampNs;
        final RigidTransform pose;

        StampedPose(long timestampNs, long unixTimestampNs, RigidTransform pose) {
            this.timestampNs = timestampNs;
            this.unixTimestampNs = unixTimestampNs;
            this.pose = pose;
        }
    }

    private final Listener listener;
    private final long maxMatchDeltaNs;
    private final Deque<StampedPose> lidarPending = new ArrayDeque<>();
    private final Deque<StampedPose> cameraPending = new ArrayDeque<>();
    private RigidTransform worldLidarFromWorldCamera;
    private int originId = Integer.MIN_VALUE;
    private long generation;
    private long matchedPosePairs;

    ArCoreLidarAlignment(Listener listener) {
        this(listener, DEFAULT_MAX_MATCH_DELTA_NS);
    }

    ArCoreLidarAlignment(Listener listener, long maxMatchDeltaNs) {
        if (maxMatchDeltaNs <= 0) {
            throw new IllegalArgumentException("Maximum pose time difference must be positive");
        }
        this.listener = listener;
        this.maxMatchDeltaNs = maxMatchDeltaNs;
    }

    synchronized void reset() {
        resetInternal();
        originId = Integer.MIN_VALUE;
        listener.onAlignmentReset(generation);
    }

    synchronized boolean isAligned() {
        return worldLidarFromWorldCamera != null;
    }

    synchronized boolean isCurrentGeneration(long candidate) {
        return generation == candidate;
    }

    synchronized long getMatchedPosePairs() {
        return matchedPosePairs;
    }

    synchronized void addLidar(long timestampNs, long unixTimestampNs, RigidTransform pose) {
        if (unixTimestampNs == Long.MIN_VALUE) {
            return;
        }
        addPending(lidarPending, new StampedPose(timestampNs, unixTimestampNs, pose));
        if (worldLidarFromWorldCamera == null) tryInitialize();
        else countMaturedPairs();
    }

    synchronized void addCamera(int newOriginId, long timestampNs, long unixTimestampNs,
                                RigidTransform pose) {
        if (newOriginId != originId) {
            if (originId != Integer.MIN_VALUE) {
                resetInternal();
                listener.onAlignmentReset(generation);
            }
            originId = newOriginId;
        }
        if (unixTimestampNs == Long.MIN_VALUE) {
            return;
        }
        addPending(cameraPending, new StampedPose(timestampNs, unixTimestampNs, pose));
        if (worldLidarFromWorldCamera != null) {
            countMaturedPairs();
            listener.onTransformedPose(generation, originId, timestampNs, transform(pose));
            return;
        }
        tryInitialize();
    }

    private void resetInternal() {
        generation++;
        worldLidarFromWorldCamera = null;
        matchedPosePairs = 0;
        lidarPending.clear();
        cameraPending.clear();
    }

    private void tryInitialize() {
        StampedPose matchedLidar = null;
        StampedPose matchedCamera = null;
        for (StampedPose camera : cameraPending) {
            if (!isMatured(camera)) break;
            StampedPose nearest = nearestLidar(camera.unixTimestampNs);
            if (nearest != null && Math.abs(nearest.unixTimestampNs
                    - camera.unixTimestampNs) <= maxMatchDeltaNs) {
                matchedLidar = nearest;
                matchedCamera = camera;
                break;
            }
        }
        if (matchedLidar == null) return;
        // Wl_T_Wc = Wl_T_L(t0) * L_T_C_arcore * inverse(Wc_T_C_arcore(t0)).
        worldLidarFromWorldCamera = matchedLidar.pose.multiply(L_T_C_ARCORE)
                .multiply(matchedCamera.pose.inverse());
        List<StampedPose> bufferedCameras = new ArrayList<>(cameraPending);
        countMaturedPairs();
        listener.onAligned(new Match(generation, originId, matchedLidar, matchedCamera,
                worldLidarFromWorldCamera, matchedPosePairs));
        for (StampedPose camera : bufferedCameras) {
            listener.onTransformedPose(generation, originId,
                    camera.timestampNs, transform(camera.pose));
        }
    }

    private boolean isMatured(StampedPose camera) {
        return !lidarPending.isEmpty()
                && lidarPending.getLast().unixTimestampNs >= camera.unixTimestampNs
                + maxMatchDeltaNs;
    }

    private StampedPose nearestLidar(long cameraUnixNs) {
        StampedPose nearest = null;
        long bestDelta = Long.MAX_VALUE;
        for (StampedPose lidar : lidarPending) {
            long delta = Math.abs(lidar.unixTimestampNs - cameraUnixNs);
            if (delta < bestDelta) {
                nearest = lidar;
                bestDelta = delta;
            }
        }
        return nearest;
    }

    private void countMaturedPairs() {
        while (!cameraPending.isEmpty() && isMatured(cameraPending.getFirst())) {
            StampedPose camera = cameraPending.removeFirst();
            StampedPose nearest = nearestLidar(camera.unixTimestampNs);
            if (nearest != null && Math.abs(nearest.unixTimestampNs
                    - camera.unixTimestampNs) <= maxMatchDeltaNs) {
                matchedPosePairs++;
            }
        }
    }

    private RigidTransform transform(RigidTransform cameraPose) {
        // Wl_T_L_arcore(t) = Wl_T_Wc * Wc_T_C_arcore(t) * C_arcore_T_L.
        return worldLidarFromWorldCamera.multiply(cameraPose).multiply(C_ARCORE_T_L);
    }

    private static void addPending(Deque<StampedPose> pending, StampedPose pose) {
        if (pending.size() == MAX_PENDING_POSES) {
            pending.removeFirst();
        }
        pending.addLast(pose);
    }

    static final class RigidTransform {
        final double x, y, z;
        final double qx, qy, qz, qw;

        RigidTransform(double x, double y, double z,
                       double qx, double qy, double qz, double qw) {
            double norm = Math.sqrt(qx * qx + qy * qy + qz * qz + qw * qw);
            if (!(norm > 0) || !Double.isFinite(norm)
                    || !Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
                throw new IllegalArgumentException("Invalid pose quaternion");
            }
            this.x = x;
            this.y = y;
            this.z = z;
            this.qx = qx / norm;
            this.qy = qy / norm;
            this.qz = qz / norm;
            this.qw = qw / norm;
        }

        RigidTransform multiply(RigidTransform other) {
            double[] rotated = rotate(other.x, other.y, other.z);
            return new RigidTransform(x + rotated[0], y + rotated[1], z + rotated[2],
                    qw * other.qx + qx * other.qw + qy * other.qz - qz * other.qy,
                    qw * other.qy - qx * other.qz + qy * other.qw + qz * other.qx,
                    qw * other.qz + qx * other.qy - qy * other.qx + qz * other.qw,
                    qw * other.qw - qx * other.qx - qy * other.qy - qz * other.qz);
        }

        RigidTransform inverse() {
            RigidTransform inverseRotation = new RigidTransform(0, 0, 0,
                    -qx, -qy, -qz, qw);
            double[] rotated = inverseRotation.rotate(-x, -y, -z);
            return new RigidTransform(rotated[0], rotated[1], rotated[2],
                    -qx, -qy, -qz, qw);
        }

        private double[] rotate(double vx, double vy, double vz) {
            double tx = 2 * (qy * vz - qz * vy);
            double ty = 2 * (qz * vx - qx * vz);
            double tz = 2 * (qx * vy - qy * vx);
            return new double[]{vx + qw * tx + qy * tz - qz * ty,
                    vy + qw * ty + qz * tx - qx * tz,
                    vz + qw * tz + qx * ty - qy * tx};
        }

        double[] matrixRowMajor() {
            return new double[]{
                    1 - 2 * (qy * qy + qz * qz), 2 * (qx * qy - qz * qw),
                    2 * (qx * qz + qy * qw), x,
                    2 * (qx * qy + qz * qw), 1 - 2 * (qx * qx + qz * qz),
                    2 * (qy * qz - qx * qw), y,
                    2 * (qx * qz - qy * qw), 2 * (qy * qz + qx * qw),
                    1 - 2 * (qx * qx + qy * qy), z,
                    0, 0, 0, 1};
        }
    }
}
