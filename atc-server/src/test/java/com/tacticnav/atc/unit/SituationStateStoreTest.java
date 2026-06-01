package com.tacticnav.atc.unit;

import com.tacticnav.atc.domain.Position;
import com.tacticnav.atc.domain.Track;
import com.tacticnav.atc.domain.TrackId;
import com.tacticnav.atc.domain.Velocity;
import com.tacticnav.atc.state.SituationStateStore;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SituationStateStoreTest {

    @Test
    void updateTracks_shouldPublishImmutableSnapshotCopy() {
        SituationStateStore store = new SituationStateStore();
        TrackId id = new TrackId("track-1");
        Map<TrackId, Track> tracks = new HashMap<>();
        tracks.put(id, track(id, 100L));

        var snapshot = store.updateTracks(tracks);
        tracks.clear();

        assertEquals(1, snapshot.trackCount());
        assertTrue(store.hasTrack(id));
        assertThrows(UnsupportedOperationException.class, () -> snapshot.tracks().clear());
    }

    private Track track(TrackId id, long timestamp) {
        return new Track(
            id,
            new Position(1.0, 2.0, 3.0, timestamp, 0.9f),
            Velocity.ZERO,
            0.9f,
            1,
            timestamp,
            timestamp
        );
    }
}
