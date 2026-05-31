package com.tacticnav.protocol;

/**
 * UDP radar observation payload shared by the radar simulator and ATC.
 *
 * @param trackId radar-local track identifier
 * @param azimuth azimuth angle in degrees
 * @param elevation elevation angle in degrees
 * @param slantRange slant range in meters
 * @param timestamp epoch millis when the measurement was taken
 */
public record RadarObservation(
        short trackId,
        float azimuth,
        float elevation,
        float slantRange,
        long timestamp
) {
}
