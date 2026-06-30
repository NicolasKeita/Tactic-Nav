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
     * Uses point-in-polygon algorithm (ray casting) for horizontal containment
     * and altitude range check.
     * 
     * Note: This implementation assumes vertices are in the same coordinate system
     * as the Position (Cartesian X=East, Y=North). For proper geospatial containment
     * with lat/lon vertices, a coordinate transformation would be needed.
     * 
     * @param pos position to test (Cartesian coordinates: x=East, y=North, z=altitude)
     * @return true if position is within zone boundaries and altitude range
     */
    public boolean contains(Position pos) {
        // Altitude check
        if (pos.z() < minAltitude || pos.z() > maxAltitude) {
            return false;
        }
        
        // Point-in-polygon check using ray casting algorithm
        // Vertices are stored as [lat0, lon0, lat1, lon1, ...]
        // For this implementation, we treat them as Cartesian (x, y) coordinates
        return isPointInPolygon(pos.x(), pos.y());
    }

    /**
     * Ray casting algorithm to determine if a point is inside a polygon.
     * Works with both convex and concave polygons.
     * 
     * @param x X coordinate of the point to test (East)
     * @param y Y coordinate of the point to test (North)
     * @return true if the point is inside the polygon
     */
    private boolean isPointInPolygon(double x, double y) {
        int numVertices = vertices.length / 2;
        boolean inside = false;
        
        for (int i = 0, j = numVertices - 1; i < numVertices; j = i++) {
            // Get vertex coordinates (treating lat as y, lon as x for Cartesian compatibility)
            // Note: This is a simplification. Proper implementation would need coordinate transformation.
            double xi = vertices[i * 2 + 1]; // lon -> x (East)
            double yi = vertices[i * 2];     // lat -> y (North)
            double xj = vertices[j * 2 + 1]; // lon -> x (East)
            double yj = vertices[j * 2];     // lat -> y (North)
            
            // Check if ray from point to right infinity intersects with polygon edge
            boolean intersects = ((yi > y) != (yj > y)) && 
                                 (x < (xj - xi) * (y - yi) / (yj - yi) + xi);
            
            if (intersects) {
                inside = !inside;
            }
        }
        
        return inside;
    }

    /**
     * Get the number of vertices in this zone boundary.
     */
    public int vertexCount() {
        return vertices.length / 2;
    }
}
