package edu.osu.pcv.marslogger;

import android.os.SystemClock;

public class TimeHelper {
    public static long bootTimeToUnixTime(long bootTimeNanos) {
        // Get the current system time in UNIX milliseconds, convert it to nanoseconds
        long currentUnixTime = System.currentTimeMillis() * 1000000L;
        // Get the current system boot time in nanoseconds
        long currentBootTime = SystemClock.elapsedRealtimeNanos();

        long timeDifference = currentBootTime - bootTimeNanos;
        return currentUnixTime - timeDifference;
    }

    public static long bootTimeToUpTime(long bootTimeNanos) {
        long currentBootTimeNanos = SystemClock.elapsedRealtimeNanos();
        long currentUpTimeNanos = System.nanoTime();
        long timeDifference = currentBootTimeNanos - bootTimeNanos;
        return currentUpTimeNanos - timeDifference;
    }
}
