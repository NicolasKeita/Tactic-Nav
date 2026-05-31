package com.tacticnav.atc.domain;

/**
 * Normalized radar input message: the result of parsing and validating a raw UDP packet.
 * 
 * This is the common currency between the parsing layer and the fusion engine.
 * All radar sources are converted into this standardized format.
 * 
 * @param radarId ID of the radar source (e.g., 1, 2, 3...)
 * @param trackId local track ID assigned by the radar (short)
 * @param azimuth azimuth in degrees (0-360)
 * @param elevation elevation in degrees (-90 to +90)
 * @param slantRange slant range in meters (distance from radar)
 * @param timestamp epoch millis when radar took the measurement
 */
public record RadarInputMessage(
        int radarId,
        short trackId,
        float azimuth,
        float elevation,
        float slantRange,
        long timestamp
) {
    public RadarInputMessage {
        if (azimuth < 0f || azimuth > 360f) {
            throw new IllegalArgumentException("azimuth must be in [0, 360]");
        }
        if (elevation < -90f || elevation > 90f) {
            throw new IllegalArgumentException("elevation must be in [-90, 90]");
        }
        if (slantRange < 0f) {
            throw new IllegalArgumentException("slantRange must be non-negative");
        }
        if (timestamp < 0) {
            throw new IllegalArgumentException("timestamp must be non-negative");
        }
    }

    /**
     * Get the normalized track ID for fusion purposes.
     * Combines radar source and local track ID.
     */
    public TrackId globalTrackId() {
        return TrackId.fromRadarSource(radarId, trackId);
    }
}
