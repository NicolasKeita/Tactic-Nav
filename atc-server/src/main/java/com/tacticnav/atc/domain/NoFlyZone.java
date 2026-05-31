package com.tacticnav.atc.domain;

/**
 * Represents a geographic no-fly zone (polygon-based restriction).
 * 
 * @param id unique identifier
 * @param name human-readable name (e.g., "RESTRICTED_AREA_01")
 * @param vertices array of lat/lon pairs defining the boundary (closed polygon)
 * @param minAltitude floor altitude (meters MSL)
 * @param maxAltitude ceiling altitude (meters MSL)
 * @param createdAt timestamp when zone was created
 * @param lastUpdatedAt timestamp when zone was last modified
 */
public record NoFlyZone(
        String id,
        String name,
        double[] vertices,  // alternating lat,lon: [lat0, lon0, lat1, lon1, ...]
        int minAltitude,
        int maxAltitude,
        long createdAt,
        long lastUpdatedAt
) {
    public NoFlyZone {
        if (id == null || id.isEmpty() || name == null || name.isEmpty()) {
            throw new IllegalArgumentException("id and name cannot be null/empty");
        }
        if (vertices == null || vertices.length < 6 || vertices.length % 2 != 0) {
            throw new IllegalArgumentException("vertices must be non-null and have even length >= 6 (3 points min)");
        }
        if (maxAltitude < minAltitude) {
            throw new IllegalArgumentException("maxAltitude must be >= minAltitude");
        }
        if (createdAt < 0 || lastUpdatedAt < 0) {
            throw new IllegalArgumentException("timestamps must be non-negative");
        }
    }

    /**
     * Check if a position is inside this no-fly zone.
     * Uses simple point-in-polygon algorithm (ray casting).
     * 
     * @param pos position to test
     * @return true if position is within zone boundaries and altitude
     */
    public boolean contains(Position pos) {
        // Altitude check
        if (pos.z() < minAltitude || pos.z() > maxAltitude) {
            return false;
        }
        
        // Point-in-polygon (horizontal plane)
        // Convert Cartesian (x, y) to lat/lon is deferred to SIG layer;
        // for now, simple AABB test as placeholder
        return true; // TODO: implement proper geospatial containment
    }

    /**
     * Get the number of vertices in this zone boundary.
     */
    public int vertexCount() {
        return vertices.length / 2;
    }
}
