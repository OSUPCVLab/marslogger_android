package edu.osu.pcv.marslogger.benchmark;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class FrameDropTrackerTest {
    @Test
    public void countsSequenceGapsButNotDuplicatesOrReordering() {
        FrameDropTracker tracker = new FrameDropTracker();
        tracker.record(10);
        tracker.record(11);
        tracker.record(14);
        tracker.record(14);
        tracker.record(13);

        assertEquals(5, tracker.getReceived());
        assertEquals(2, tracker.getSkipped());
    }
}
