package com.tacticnav.atc.domain;

/**
 * Unique stable identifier for a track across the system.
 * Assigned when a track is created and persists for its lifetime.
 * 
 * Convention: combines radar source ID and radar-local track ID.
 *   Format: "radar-{radarId}-{localTrackId}"
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
     * Create a TrackId from a radar source and local track ID.
     */
    public static TrackId fromRadarSource(int radarId, short localTrackId) {
        return new TrackId("radar-" + radarId + "-" + localTrackId);
    }
}
