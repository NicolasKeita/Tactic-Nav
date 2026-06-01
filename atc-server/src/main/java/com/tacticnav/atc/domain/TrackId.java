package com.tacticnav.atc.domain;

/**
 * Unique stable identifier for a track across the system.
 * Assigned when a track is created and persists for its lifetime.
 * 
 * Convention: derived from the track ID carried by the radar datagram.
 *   Format: "track-{observationTrackId}"
 */
public record TrackId(String value) {
    public TrackId {
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException("TrackId value cannot be null or empty");
        }
    }

    @Override
    public String toString() {
        return value;
    }

    /**
     * Create a TrackId from an observation track ID.
     */
    public static TrackId fromObservation(short observationTrackId) {
        return new TrackId("track-" + observationTrackId);
    }
}
