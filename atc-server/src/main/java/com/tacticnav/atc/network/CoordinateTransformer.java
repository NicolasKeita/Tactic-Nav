package com.tacticnav.atc.network;

import com.tacticnav.atc.domain.Position;

/**
 * Converts spherical radar coordinates to Cartesian/geodetic coordinates.
 * 
 * Spherical (radar-centric):
 *   - Azimuth: 0° = North, 90° = East (clockwise)
 *   - Elevation: 0° = horizon, 90° = zenith
 *   - Slant range: direct distance from radar
 * 
 * Cartesian (assumed radar at origin):
 *   - X: East (positive = away from reference meridian)
 *   - Y: North (positive = towards north pole)
 *   - Z: altitude (positive = up)
 * 
 * Note: This is a simplified conversion. For production, would integrate with
 * WGS84 geodetic transformations and proper map projection.
 */
public final class CoordinateTransformer {
    private CoordinateTransformer() {}

    /**
     * Convert spherical coordinates to Cartesian.
     * 
     * Assumes:
     *   - Radar is at origin (0, 0, radarAltitude)
     *   - Azimuth 0° is North, 90° is East
     *   - Elevation 0° is horizon, 90° is up
     * 
     * @param azimuth degrees (0-360)
     * @param elevation degrees (-90 to +90)
     * @param slantRange meters
     * @param radarAltitude altitude of radar (meters MSL)
     * @param timestamp measurement timestamp
     * @param confidence measurement confidence (0.0-1.0)
     * @return Cartesian position
     */
    public static Position toCartesian(
            float azimuth,
            float elevation,
            float slantRange,
            double radarAltitude,
            long timestamp,
            float confidence
    ) {
        // Convert to radians
        double az_rad = Math.toRadians(azimuth);
        double el_rad = Math.toRadians(elevation);

        // Compute horizontal and vertical components
        double r_horizontal = slantRange * Math.cos(el_rad);
        double z_offset = slantRange * Math.sin(el_rad);

        // Convert to Cartesian (with azimuth 0° = North)
        // Standard: angle 0 = East (positive X), π/2 = North (positive Y)
        // Radar: azimuth 0 = North, 90 = East
        // So we rotate: Y = r*cos(az), X = r*sin(az)
        double y = r_horizontal * Math.cos(az_rad);  // North component
        double x = r_horizontal * Math.sin(az_rad);  // East component
        double z = radarAltitude + z_offset;

        return new Position(x, y, z, timestamp, confidence);
    }

    /**
     * Convert Cartesian to spherical (inverse operation).
     * Useful for debugging or converting track state back to radar coordinates.
     */
    public static SphericalCoords toSpherical(Position pos, double radarAltitude) {
        double x = pos.x();
        double y = pos.y();
        double z = pos.z();

        // Compute azimuth and elevation
        double r_horizontal = Math.sqrt(x * x + y * y);
        double slantRange = Math.sqrt(r_horizontal * r_horizontal + (z - radarAltitude) * (z - radarAltitude));
        
        double azimuth = Math.atan2(x, y);  // atan2(east, north)
        if (azimuth < 0) azimuth += 2 * Math.PI;
        
        double elevation = Math.atan2(z - radarAltitude, r_horizontal);

        return new SphericalCoords(
            (float) Math.toDegrees(azimuth),
            (float) Math.toDegrees(elevation),
            (float) slantRange
        );
    }

    /**
     * Represents spherical coordinates.
     */
    public record SphericalCoords(float azimuth, float elevation, float slantRange) {}
}
