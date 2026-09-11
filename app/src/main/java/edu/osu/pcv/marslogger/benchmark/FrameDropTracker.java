package edu.osu.pcv.marslogger.benchmark;

/** Counts positive sequence gaps. Duplicate, reordered, and wraparound frames do not create gaps. */
public final class FrameDropTracker {
    private boolean hasLast;
    private long lastSequence;
    private long received;
    private long skipped;

    public synchronized void record(long sequence) {
        received++;
        if (hasLast && sequence > lastSequence + 1) {
            skipped += sequence - lastSequence - 1;
        }
        if (!hasLast || sequence > lastSequence) {
            lastSequence = sequence;
            hasLast = true;
        }
    }

    public synchronized void clear() {
        hasLast = false;
        lastSequence = 0;
        received = 0;
        skipped = 0;
    }

    public synchronized long getReceived() {
        return received;
    }

    public synchronized long getSkipped() {
        return skipped;
    }
}
