package com.tacticnav.radar;

/**
 * Represents a radar track in spherical coordinates.
 *
 * @param trackId    unique track identifier
 * @param azimuth    azimuth angle in degrees (0-360)
 * @param elevation  elevation angle in degrees (-90 to +90)
 * @param slantRange slant range in meters
 * @param timestamp  epoch millis when the measurement was taken
 */
public record Track(short trackId, float azimuth, float elevation, float slantRange, long timestamp) {
}