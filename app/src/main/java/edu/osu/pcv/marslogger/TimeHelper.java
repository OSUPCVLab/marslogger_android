package edu.osu.pcv.marslogger;

import android.os.SystemClock;

public class TimeHelper {
    public static long monotonicToUnixTime(long monoTimeNanos) {
        long unixNowNs =
                System.currentTimeMillis() * 1000000L;
        long monoNowNs =
                SystemClock.elapsedRealtimeNanos();
        return unixNowNs +
                (monoTimeNanos - monoNowNs);
    }
}
