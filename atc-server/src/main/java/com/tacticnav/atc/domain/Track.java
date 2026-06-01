package com.tacticnav.atc.domain;

/**
 * Represents a consolidated track after track fusion of radar observations.
 * 
 * Lifecycle:
 *   1. Created when a new target is detected
 *   2. Updated with periodic measurements from radars
 *   3. Expired after TTL if no updates received
 *   4. Removed from the air situation
 * 
 * This is an immutable record. The track fusion engine creates new Track instances
 * rather than mutating existing ones. This provides:
 *   - Safe concurrent reads
 *   - Clear causality and version history
 *   - Consistent snapshots
 * 
 * @param id global stable track identifier
 * @param position latest known position + timestamp
 * @param velocity estimated velocity (dx, dy, dz per second)
 * @param confidence overall track confidence (0.0-1.0)
 * @param updateCount total number of updates received
 * @param createdAt epoch millis when track was created
 * @param lastUpdatedAt epoch millis of most recent update
 */
public record Track(
        TrackId id,
        Position position,
        Velocity velocity,
        float confidence,
        int updateCount,
        long createdAt,
        long lastUpdatedAt
) {
    public Track {
        if (id == null || position == null || velocity == null) {
            throw new IllegalArgumentException("id, position, and velocity cannot be null");
        }
        if (confidence < 0f || confidence > 1f) {
            throw new IllegalArgumentException("confidence must be in [0.0, 1.0]");
        }
        if (updateCount < 0) {
            throw new IllegalArgumentException("updateCount must be non-negative");
        }
        if (createdAt < 0 || lastUpdatedAt < 0) {
            throw new IllegalArgumentException("timestamps must be non-negative");
        }
        if (lastUpdatedAt < createdAt) {
            throw new IllegalArgumentException("lastUpdatedAt must be >= createdAt");
        }
    }

    /**
     * Check if this track is stale (older than TTL).
     * Default TTL: 5 seconds without update.
     */
    public boolean isStale(long currentTimeMillis) {
        return isStale(currentTimeMillis, 5000);
    }

    /**
     * Check if this track is stale for the supplied TTL.
     */
    public boolean isStale(long currentTimeMillis, long ttlMillis) {
        return currentTimeMillis - lastUpdatedAt > ttlMillis;
    }

    /**
     * Get the age of this track since creation (milliseconds).
     */
    public long ageMillis(long currentTimeMillis) {
        return currentTimeMillis - createdAt;
    }

    /**
     * Estimate track position at a future time based on velocity.
     * Useful for predictive display or collision detection.
     */
    public Position estimatePositionAt(long timeMillis) {
        if (timeMillis < lastUpdatedAt) {
            return position; // Don't extrapolate backwards
        }
        double dt = (timeMillis - position.timestamp()) / 1000.0; // seconds
        double newX = position.x() + velocity.vx() * dt;
        double newY = position.y() + velocity.vy() * dt;
        double newZ = position.z() + velocity.vz() * dt;
        return new Position(newX, newY, newZ, timeMillis, position.confidence());
    }
}
