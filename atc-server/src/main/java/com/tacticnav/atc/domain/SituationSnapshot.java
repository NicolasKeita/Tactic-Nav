package com.tacticnav.atc.domain;

import java.util.List;
import java.util.Map;

/**
 * Immutable snapshot of the entire tactical air situation at a given moment.
 * 
 * Contains:
 *   - All active radar tracks
 *   - All no-fly zones
 *   - Timestamp of snapshot
 * 
 * This is the primary data structure shared between track fusion and
 * readers. It is read-only and safe to share across threads.
 * 
 * @param timestamp epoch millis when this snapshot was captured
 * @param tracks map of TrackId → Track (all active tracks)
 * @param zones list of active no-fly zones
 * @param sequenceNumber monotonically increasing version number
 */
public record SituationSnapshot(
        long timestamp,
        Map<TrackId, Track> tracks,
        List<NoFlyZone> zones,
        long sequenceNumber
) {
    public SituationSnapshot {
        if (timestamp < 0) {
            throw new IllegalArgumentException("timestamp must be non-negative");
        }
        if (tracks == null || zones == null) {
            throw new IllegalArgumentException("tracks and zones cannot be null");
        }
        if (sequenceNumber < 0) {
            throw new IllegalArgumentException("sequenceNumber must be non-negative");
        }
    }

    /**
     * Count of active tracks in this snapshot.
     */
    public int trackCount() {
        return tracks.size();
    }

    /**
     * Count of active no-fly zones in this snapshot.
     */
    public int zoneCount() {
        return zones.size();
    }

    /**
     * Find a track by ID.
     */
    public Track findTrack(TrackId id) {
        return tracks.get(id);
    }

}
