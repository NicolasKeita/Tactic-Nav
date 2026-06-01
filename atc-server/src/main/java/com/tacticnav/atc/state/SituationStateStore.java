package com.tacticnav.atc.state;

import com.tacticnav.atc.domain.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Thread-safe store of the current air situation.
 * 
 * Provides:
 *   - Safe concurrent reads via snapshots
 *   - Single-writer update semantics (track fusion engine)
 *   - Consistent views for readers
 * 
 * Design:
 *   - Holds one volatile SituationSnapshot reference
 *   - Updates publish a complete immutable snapshot atomically
 *   - Readers never block the track fusion writer
 * 
 * Concurrency Model:
 *   - Many concurrent readers (monitoring threads, UI queries)
 *   - Single writer (track fusion engine thread)
 *   - Snapshot replacement avoids global locks
 */
public class SituationStateStore {
    
    private final AtomicLong nextSequenceNumber = new AtomicLong(1);
    private volatile SituationSnapshot current;

    /**
     * Initialize with empty situation.
     */
    public SituationStateStore() {
        this.current = new SituationSnapshot(
            System.currentTimeMillis(),
            Map.of(),
            List.of(),
            0
        );
    }

    /**
     * Get current situation snapshot.
     * Safe for concurrent reads.
     * 
     * @return immutable snapshot
     */
    public SituationSnapshot getSnapshot() {
        return current;
    }

    /**
     * Update situation with new tracks and zones.
     * Should be called only by track fusion engine (single writer).
     * 
     * @param tracks map of all active tracks
     * @param zones list of all active zones
     * @return the newly created snapshot
     */
    public SituationSnapshot update(Map<TrackId, Track> tracks, List<NoFlyZone> zones) {
        SituationSnapshot newSnapshot = new SituationSnapshot(
            System.currentTimeMillis(),
            Map.copyOf(tracks),
            List.copyOf(zones),
            nextSequenceNumber.getAndIncrement()
        );
        current = newSnapshot;
        return newSnapshot;
    }

    /**
     * Update situation with new tracks only (zones unchanged).
     * Convenience for common case.
     */
    public SituationSnapshot updateTracks(Map<TrackId, Track> tracks) {
        SituationSnapshot snapshot = current;
        SituationSnapshot newSnapshot = new SituationSnapshot(
            System.currentTimeMillis(),
            Map.copyOf(tracks),
            snapshot.zones(),
            nextSequenceNumber.getAndIncrement()
        );
        current = newSnapshot;
        return newSnapshot;
    }

    /**
     * Update situation with new zones only (tracks unchanged).
     */
    public SituationSnapshot updateZones(List<NoFlyZone> zones) {
        SituationSnapshot snapshot = current;
        SituationSnapshot newSnapshot = new SituationSnapshot(
            System.currentTimeMillis(),
            snapshot.tracks(),
            List.copyOf(zones),
            nextSequenceNumber.getAndIncrement()
        );
        current = newSnapshot;
        return newSnapshot;
    }

    /**
     * Get track count.
     */
    public int getTrackCount() {
        return current.trackCount();
    }

    /**
     * Get zone count.
     */
    public int getZoneCount() {
        return current.zoneCount();
    }

    /**
     * Check if a track exists.
     */
    public boolean hasTrack(TrackId id) {
        return current.tracks().containsKey(id);
    }

    /**
     * Get a specific track by ID.
     */
    public Track getTrack(TrackId id) {
        return current.tracks().get(id);
    }
}
