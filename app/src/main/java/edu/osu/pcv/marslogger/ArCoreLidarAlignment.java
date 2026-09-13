package edu.osu.pcv.marslogger;

import java.util.ArrayDeque;
import java.util.Deque;

/** One-time alignment of ARCore camera poses to the LiDAR mapping world. */
final class ArCoreLidarAlignment {
    static final long MAX_MATCH_DELTA_NS = 75_000_000L;
    private static final long MAX_CLOCK_AGE_NS = 5_000_000_000L;
    private static final int MAX_PENDING_POSES = 64;

    // CAD T_lidar_camera = [tx, ty, tz, qx, qy, qz, qw], in metres.
    static final RigidTransform L_T_C = new RigidTransform(
            0.09165818567908458, 0.024371359183858687, -0.07761523102914086,
            -0.006557708581064656, -0.5943650948131397,
            0.8041392764010252, 0.006866926180545729);
    private static final RigidTransform C_T_L = L_T_C.inverse();

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
        final long lidarBootTimestampNs;
        final long cameraBootTimestampNs;
        final long differenceNs;
        final RigidTransform worldLidarFromWorldCamera;

        Match(long generation, int originId, StampedPose lidar, StampedPose camera,
              RigidTransform worldLidarFromWorldCamera) {
            this.generation = generation;
            this.originId = originId;
            lidarTimestampNs = lidar.timestampNs;
            cameraTimestampNs = camera.timestampNs;
            lidarBootTimestampNs = lidar.bootTimestampNs;
            cameraBootTimestampNs = camera.bootTimestampNs;
            differenceNs = Math.abs(lidar.bootTimestampNs - camera.bootTimestampNs);
            this.worldLidarFromWorldCamera = worldLidarFromWorldCamera;
        }
    }

    private static final class StampedPose {
        final long timestampNs;
        final long bootTimestampNs;
        final RigidTransform pose;

        StampedPose(long timestampNs, long bootTimestampNs, RigidTransform pose) {
            this.timestampNs = timestampNs;
            this.bootTimestampNs = bootTimestampNs;
            this.pose = pose;
        }
    }

    private final Listener listener;
    private final Deque<StampedPose> lidarPending = new ArrayDeque<>();
    private final Deque<StampedPose> cameraPending = new ArrayDeque<>();
    private RigidTransform worldLidarFromWorldCamera;
    private int originId = Integer.MIN_VALUE;
    private long generation;

    ArCoreLidarAlignment(Listener listener) {
        this.listener = listener;
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

    synchronized void addLidar(long timestampNs, long bootTimestampNs, RigidTransform pose) {
        if (bootTimestampNs == Long.MIN_VALUE || worldLidarFromWorldCamera != null) {
            return;
        }
        addPending(lidarPending, new StampedPose(timestampNs, bootTimestampNs, pose));
        tryInitialize();
    }

    synchronized void addCamera(int newOriginId, long timestampNs, long bootTimestampNs,
                                RigidTransform pose) {
        if (newOriginId != originId) {
            if (originId != Integer.MIN_VALUE) {
                resetInternal();
                listener.onAlignmentReset(generation);
            }
            originId = newOriginId;
        }
        if (bootTimestampNs == Long.MIN_VALUE) {
            return;
        }
        if (worldLidarFromWorldCamera != null) {
            listener.onTransformedPose(generation, originId, timestampNs, transform(pose));
            return;
        }
        addPending(cameraPending, new StampedPose(timestampNs, bootTimestampNs, pose));
        tryInitialize();
    }

    private void resetInternal() {
        generation++;
        worldLidarFromWorldCamera = null;
        lidarPending.clear();
        cameraPending.clear();
    }

    private void tryInitialize() {
        StampedPose matchedLidar = null;
        StampedPose matchedCamera = null;
        long bestDifference = MAX_MATCH_DELTA_NS + 1;
        for (StampedPose lidar : lidarPending) {
            for (StampedPose camera : cameraPending) {
                long difference = Math.abs(lidar.bootTimestampNs - camera.bootTimestampNs);
                if (difference < bestDifference) {
                    bestDifference = difference;
                    matchedLidar = lidar;
                    matchedCamera = camera;
                }
            }
        }
        if (matchedLidar == null || bestDifference > MAX_MATCH_DELTA_NS) {
            return;
        }
        // Wl_T_Wc = Wl_T_L(t0) * L_T_C * inverse(Wc_T_C(t0)).
        worldLidarFromWorldCamera = matchedLidar.pose.multiply(L_T_C)
                .multiply(matchedCamera.pose.inverse());
        listener.onAligned(new Match(generation, originId, matchedLidar, matchedCamera,
                worldLidarFromWorldCamera));
        for (StampedPose camera : cameraPending) {
            listener.onTransformedPose(generation, originId,
                    camera.timestampNs, transform(camera.pose));
        }
        lidarPending.clear();
        cameraPending.clear();
    }

    private RigidTransform transform(RigidTransform cameraPose) {
        // Wl_T_L_arcore(t) = Wl_T_Wc * Wc_T_C(t) * C_T_L.
        return worldLidarFromWorldCamera.multiply(cameraPose).multiply(C_T_L);
    }

    private static void addPending(Deque<StampedPose> pending, StampedPose pose) {
        if (pending.size() == MAX_PENDING_POSES) {
            pending.removeFirst();
        }
        pending.addLast(pose);
    }

    /** Normalize a fresh Android/ROS stamp to elapsedRealtimeNanos; reject unknown clocks. */
    static long normalizeToBootTime(long timestampNs, long bootNowNs,
                                    long monotonicNowNs, long unixNowNs) {
        if (timestampNs <= 0) {
            return Long.MIN_VALUE;
        }
        long bootAge = Math.abs(timestampNs - bootNowNs);
        long monotonicAge = Math.abs(timestampNs - monotonicNowNs);
        long unixAge = Math.abs(timestampNs - unixNowNs);
        long nearestAge = Math.min(bootAge, Math.min(monotonicAge, unixAge));
        if (nearestAge > MAX_CLOCK_AGE_NS) {
            return Long.MIN_VALUE;
        }
        if (bootAge == nearestAge) {
            return timestampNs;
        }
        return timestampNs + bootNowNs - (monotonicAge == nearestAge
                ? monotonicNowNs : unixNowNs);
    }

    static final class RigidTransform {
        final double x, y, z;
        final double qx, qy, qz, qw;

        RigidTransform(double x, double y, double z,
                       double qx, double qy, double qz, double qw) {
            double norm = Math.sqrt(qx * qx + qy * qy + qz * qz + qw * qw);
            if (!(norm > 0) || !Double.isFinite(norm)) {
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
