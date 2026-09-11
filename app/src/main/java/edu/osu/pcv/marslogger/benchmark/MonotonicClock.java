package edu.osu.pcv.marslogger.benchmark;

import android.os.SystemClock;

public interface MonotonicClock {
    long nowNanos();

    MonotonicClock SYSTEM = new MonotonicClock() {
        @Override
        public long nowNanos() {
            return SystemClock.elapsedRealtimeNanos();
        }
    };
}
