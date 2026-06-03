package com.tacticnav.cockpit.core;

import com.tacticnav.cockpit.data.AtcTrackSource;
import com.tacticnav.cockpit.domain.GeoPoint;
import com.tacticnav.cockpit.domain.LinkStatus;
import com.tacticnav.cockpit.domain.TacticalSnapshot;
import com.tacticnav.cockpit.domain.TacticalTrack;
import com.tacticnav.cockpit.domain.TrackStatus;
import com.tacticnav.cockpit.processing.SituationProcessor;
import com.tacticnav.cockpit.time.Clock;

import org.junit.Test;

import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class CockpitControllerTest {
    @Test
    public void startDeliversProcessedSnapshotToListener() throws Exception {
        TacticalSnapshot rawSnapshot = new TacticalSnapshot(
                1_000L,
                5L,
                Collections.singletonList(trackAt(new GeoPoint(43.9000, -0.5000), 1_000L)),
                Collections.emptyList(),
                LinkStatus.SIMULATED,
                null
        );

        TestAtcSource source = new TestAtcSource(rawSnapshot);
        CockpitController controller = new CockpitController(source, new SituationProcessor(new FixedClock(1_000L)));

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<TacticalSnapshot> received = new AtomicReference<>();

        controller.start(new CockpitController.Listener() {
            @Override
            public void onSituation(TacticalSnapshot snapshot) {
                received.set(snapshot);
                latch.countDown();
            }

            @Override
            public void onError(Throwable error) {
                latch.countDown();
            }
        });

        assertTrue(latch.await(1, TimeUnit.SECONDS));
        assertEquals(5L, received.get().sequenceNumber());
        assertEquals(TrackStatus.NORMAL, received.get().tracks().get(0).status());
        controller.stop();
    }

    @Test
    public void stopPreventsFurtherSnapshotDelivery() throws Exception {
        TacticalSnapshot rawSnapshot = new TacticalSnapshot(
                2_000L,
                7L,
                Collections.singletonList(trackAt(new GeoPoint(43.9000, -0.5000), 2_000L)),
                Collections.emptyList(),
                LinkStatus.SIMULATED,
                null
        );

        TestAtcSource source = new TestAtcSource(rawSnapshot);
        CockpitController controller = new CockpitController(source, new SituationProcessor(new FixedClock(2_000L)));

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<TacticalSnapshot> received = new AtomicReference<>();

        controller.start(new CockpitController.Listener() {
            @Override
            public void onSituation(TacticalSnapshot snapshot) {
                received.set(snapshot);
                latch.countDown();
            }

            @Override
            public void onError(Throwable error) {
                latch.countDown();
            }
        });

        assertTrue(latch.await(1, TimeUnit.SECONDS));
        controller.stop();

        source.emitSnapshotAfterStop();
        Thread.sleep(100);

        assertFalse(source.wasDeliveredAfterStop());
    }

    private static TacticalTrack trackAt(GeoPoint position, long timestamp) {
        return new TacticalTrack(
                "track-1",
                "TEST 1",
                position,
                12_000,
                90.0f,
                300.0f,
                0.0f,
                0.9f,
                timestamp,
                TrackStatus.NORMAL
        );
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

    private static final class TestAtcSource implements AtcTrackSource {
        private final TacticalSnapshot snapshot;
        private volatile AtcTrackSource.Listener listener;
        private volatile boolean deliveredAfterStop;

        private TestAtcSource(TacticalSnapshot snapshot) {
            this.snapshot = snapshot;
        }

        @Override
        public void start(Listener listener) {
            this.listener = listener;
            listener.onSnapshot(snapshot);
        }

        @Override
        public void stop() {
            listener = null;
        }

        public void emitSnapshotAfterStop() {
            if (listener != null) {
                listener.onSnapshot(snapshot);
                deliveredAfterStop = true;
            }
        }

        public boolean wasDeliveredAfterStop() {
            return deliveredAfterStop;
        }
    }
}
