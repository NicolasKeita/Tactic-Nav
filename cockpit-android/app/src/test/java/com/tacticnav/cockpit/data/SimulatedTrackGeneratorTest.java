package com.tacticnav.cockpit.data;

import com.tacticnav.cockpit.domain.LinkStatus;
import com.tacticnav.cockpit.domain.TacticalSnapshot;
import com.tacticnav.cockpit.domain.TrackStatus;
import com.tacticnav.cockpit.processing.SituationProcessor;
import com.tacticnav.cockpit.time.Clock;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public final class SimulatedTrackGeneratorTest {
    @Test
    public void snapshotAtProducesDeterministicSituationShape() {
        SimulatedTrackGenerator generator = new SimulatedTrackGenerator(1_000L);

        TacticalSnapshot snapshot = generator.snapshotAt(1_000L, 42L);

        assertEquals(42L, snapshot.sequenceNumber());
        assertEquals(LinkStatus.SIMULATED, snapshot.linkStatus());
        assertEquals(9, snapshot.trackCount());
        assertEquals(2, snapshot.zones().size());
    }

    @Test
    public void simulatedScenarioCreatesIntrusionDuringMiddleOfCycle() {
        long now = 47_000L;
        SimulatedTrackGenerator generator = new SimulatedTrackGenerator(1_000L);
        SituationProcessor processor = new SituationProcessor(new FixedClock(now));

        TacticalSnapshot processed = processor.process(generator.snapshotAt(now, 4L));

        assertNotNull(processed.alert());
        assertTrue(hasIntruder(processed));
    }

    private static boolean hasIntruder(TacticalSnapshot snapshot) {
        for (int i = 0; i < snapshot.tracks().size(); i++) {
            if (snapshot.tracks().get(i).status() == TrackStatus.INTRUDER) {
                return true;
            }
        }
        return false;
    }

    private static final class FixedClock implements Clock {
        private final long nowMillis;

        private FixedClock(long nowMillis) {
            this.nowMillis = nowMillis;
        }

        @Override
        public long nowMillis() {
            return nowMillis;
        }
    }
}
