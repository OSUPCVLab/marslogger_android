package edu.osu.pcv.marslogger.benchmark;

import java.io.File;

public final class BenchmarkSession {
    public final String sessionId;
    public final long startElapsedRealtimeNanos;
    public final File pipelineFile;
    public final File deviceStatsFile;

    BenchmarkSession(String sessionId, long startElapsedRealtimeNanos,
                     File pipelineFile, File deviceStatsFile) {
        this.sessionId = sessionId;
        this.startElapsedRealtimeNanos = startElapsedRealtimeNanos;
        this.pipelineFile = pipelineFile;
        this.deviceStatsFile = deviceStatsFile;
    }
}
