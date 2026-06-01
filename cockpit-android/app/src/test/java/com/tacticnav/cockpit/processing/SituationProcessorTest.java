package com.tacticnav.cockpit.processing;

import com.tacticnav.cockpit.domain.GeoPoint;
import com.tacticnav.cockpit.domain.LinkStatus;
import com.tacticnav.cockpit.domain.NoFlyZone;
import com.tacticnav.cockpit.domain.TacticalSnapshot;
import com.tacticnav.cockpit.domain.TacticalTrack;
import com.tacticnav.cockpit.domain.TrackStatus;
import com.tacticnav.cockpit.time.Clock;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public final class SituationProcessorTest {
    private static final long NOW = 10_000L;

    @Test
    public void processMarksTrackAsIntruderWhenInsideNoFlyZone() {
        SituationProcessor processor = new SituationProcessor(new FixedClock(NOW));
        TacticalSnapshot snapshot = snapshot(trackAt(new GeoPoint(43.9000, -0.5000), NOW), zone());

        TacticalSnapshot processed = processor.process(snapshot);

        assertEquals(TrackStatus.INTRUDER, processed.tracks().get(0).status());
        assertNotNull(processed.alert());
        assertEquals("track-1", processed.alert().trackId());
    }

    @Test
    public void processPrefersStaleOverIntruderForOldTrack() {
        SituationProcessor processor = new SituationProcessor(new FixedClock(NOW));
        TacticalTrack staleTrack = trackAt(new GeoPoint(43.9000, -0.5000), NOW - 3_000L);

        TacticalSnapshot processed = processor.process(snapshot(staleTrack, zone()));

        assertEquals(TrackStatus.STALE, processed.tracks().get(0).status());
        assertNull(processed.alert());
    }

    @Test
    public void processMarksLiveLinkAsLostWhenSnapshotIsTooOld() {
        SituationProcessor processor = new SituationProcessor(new FixedClock(NOW));
        TacticalSnapshot snapshot = new TacticalSnapshot(
                NOW - 4_000L,
                1L,
                Collections.singletonList(trackAt(new GeoPoint(43.7000, -0.7000), NOW)),
                Collections.singletonList(zone()),
                LinkStatus.LIVE,
                null
        );

        TacticalSnapshot processed = processor.process(snapshot);

        assertEquals(LinkStatus.LOST, processed.linkStatus());
    }

    private static TacticalSnapshot snapshot(TacticalTrack track, NoFlyZone zone) {
        return new TacticalSnapshot(
                NOW,
                1L,
                Collections.singletonList(track),
                Collections.singletonList(zone),
                LinkStatus.LIVE,
                null
        );
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

    private static NoFlyZone zone() {
        return new NoFlyZone(
                "NFZ-TEST",
                "NFZ TEST",
                Arrays.asList(
                        new GeoPoint(43.8800, -0.5400),
                        new GeoPoint(43.9250, -0.5350),
                        new GeoPoint(43.9250, -0.4650),
                        new GeoPoint(43.8800, -0.4650)
                ),
                0,
                40_000
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
}
