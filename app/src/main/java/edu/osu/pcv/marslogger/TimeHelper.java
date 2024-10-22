package edu.osu.pcv.marslogger;

public class TimeHelper {
    public static long upTimeToUnixTime(long upTimeNanos) {
        // Get the current system time in UNIX milliseconds, convert it to nanoseconds
        long currentTimeNanos = System.currentTimeMillis() * 1000000L;
        // Get the current system uptime in nanoseconds
        long systemNanoTime = System.nanoTime();

        long timeDifferenceNanos = systemNanoTime - upTimeNanos;
        return currentTimeNanos - timeDifferenceNanos;
    }
}
