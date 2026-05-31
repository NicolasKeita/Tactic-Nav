package com.tacticnav.atc.domain;

/**
 * Represents a 3D position in space.
 * 
 * After track fusion, positions are converted from spherical (azimuth, elevation, slant range)
 * to Cartesian or geodetic coordinates. This record represents the normalized form.
 * 
 * @param x X coordinate (meters, relative to radar origin)
 * @param y Y coordinate (meters, relative to radar origin)
 * @param z Z coordinate / altitude (meters MSL)
 * @param timestamp epoch millis when measurement was taken
 * @param confidence measurement confidence (0.0-1.0); lower = less certain
 */
public record Position(
        double x,
        double y,
        double z,
        long timestamp,
        float confidence
) {
    public Position {
        if (confidence < 0f || confidence > 1f) {
            throw new IllegalArgumentException("confidence must be in [0.0, 1.0]");
        }
        if (timestamp < 0) {
            throw new IllegalArgumentException("timestamp must be non-negative");
        }
    }

    /**
     * Calculate Euclidean distance to another position (ignoring time).
     * Used in track association logic.
     */
    public double distanceTo(Position other) {
        double dx = this.x - other.x;
        double dy = this.y - other.y;
        double dz = this.z - other.z;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /**
     * Age of this measurement in milliseconds (from given current time).
     */
    public long ageMillis(long currentTimeMillis) {
        return currentTimeMillis - this.timestamp;
    }
}
