package com.tacticnav.atc.unit;

import com.tacticnav.atc.domain.RadarInputMessage;
import com.tacticnav.atc.domain.Track;
import com.tacticnav.atc.domain.TrackId;
import com.tacticnav.atc.fusion.TrackFusionEngine;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TrackFusionEngineTest {

    @Test
    void fuse_shouldIgnoreOutOfOrderObservationForExistingTrack() {
        TrackFusionEngine engine = new TrackFusionEngine(0.0, 0.0, 100.0);
        Map<TrackId, Track> tracks = new HashMap<>();

        RadarInputMessage first = new RadarInputMessage(1, (short) 5, 90f, 0f, 1_000f, 2_000L);
        engine.fuse(first, tracks, 2_000L);

        TrackId id = first.globalTrackId();
        Track before = tracks.get(id);

        RadarInputMessage older = new RadarInputMessage(1, (short) 5, 180f, 0f, 5_000f, 1_000L);
        var result = engine.fuse(older, tracks, 2_100L);

        Track after = tracks.get(id);
        assertEquals(before, after);
        assertTrue(result.events().isEmpty());
    }

    @Test
    void fuse_shouldExpireStaleTracksBeforeAssociation() {
        TrackFusionEngine engine = new TrackFusionEngine(0.0, 0.0, 100.0);
        Map<TrackId, Track> tracks = new HashMap<>();

        RadarInputMessage first = new RadarInputMessage(1, (short) 5, 90f, 0f, 1_000f, 1_000L);
        engine.fuse(first, tracks, 1_000L);

        RadarInputMessage second = new RadarInputMessage(2, (short) 8, 90f, 0f, 1_000f, 7_100L);
        var result = engine.fuse(second, tracks, 7_100L);

        assertEquals(1, tracks.size());
        assertTrue(tracks.containsKey(second.globalTrackId()));
        assertEquals(2, result.events().size());
    }
}
