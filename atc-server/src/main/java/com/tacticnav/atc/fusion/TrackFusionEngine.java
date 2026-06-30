package com.tacticnav.atc.fusion;

import com.tacticnav.atc.domain.*;
import com.tacticnav.atc.network.CoordinateTransformer;
import java.util.*;

/**
 * Core track association and track fusion logic.
 * 
 * Responsible for:
 *   1. Matching incoming radar observations to existing tracks
 *   2. Creating new tracks for unmatched observations
 *   3. Computing velocity estimates
 *   4. Expiring stale tracks
 * 
 * Algorithm: Distance-based association with gating.
 * 
 * For each incoming observation:
 *   1. Expire stale tracks
 *   2. Convert spherical to Cartesian coordinates
 *   3. Prefer an active observation track ID
 *   4. Find candidate existing tracks within a distance gate
 *   5. If one or more candidates exist: pick closest (nearest-neighbor)
 *   6. If no candidates exist: create a new track
 *   7. Ignore out-of-order observations for already matched tracks
 * 
 * Default parameters:
 *   - associationGateDistance: 500.0 meters
 *   - trackTTL: 5000 milliseconds
 */
public final class TrackFusionEngine {
    
    private final double associationGateDistance;
    private final long trackTTL;
    
    /**
     * Create a TrackFusionEngine with custom parameters.
     * 
     * @param associationGateDistance maximum distance in meters for track association
     * @param trackTTL time-to-live in milliseconds for tracks without updates
     * @throws IllegalArgumentException if parameters are invalid
     */
    public TrackFusionEngine(double associationGateDistance, long trackTTL) {
        if (associationGateDistance <= 0) {
            throw new IllegalArgumentException("associationGateDistance must be positive");
        }
        if (trackTTL <= 0) {
            throw new IllegalArgumentException("trackTTL must be positive");
        }
        this.associationGateDistance = associationGateDistance;
        this.trackTTL = trackTTL;
    }
    
    /**
     * Create a TrackFusionEngine with default parameters.
     * 
     * @return engine with default configuration (500m gate, 5s TTL)
     */
    public static TrackFusionEngine withDefaults() {
        return new TrackFusionEngine(500.0, 5000);
    }
    
    /**
     * Create a TrackFusionEngine with only custom association gate distance.
     * Uses default TTL of 5000ms.
     * 
     * @param associationGateDistance maximum distance in meters for track association
     * @return engine with custom gate distance and default TTL
     */
    public static TrackFusionEngine withCustomGate(double associationGateDistance) {
        return new TrackFusionEngine(associationGateDistance, 5000);
    }
    
    /**
     * Create a TrackFusionEngine with only custom TTL.
     * Uses default gate distance of 500m.
     * 
     * @param trackTTL time-to-live in milliseconds for tracks without updates
     * @return engine with custom TTL and default gate distance
     */
    public static TrackFusionEngine withCustomTTL(long trackTTL) {
        return new TrackFusionEngine(500.0, trackTTL);
    }

    /**
     * Process an incoming radar observation and update track state.
     * 
     * @param message normalized radar observation
     * @param currentTracks existing tracks (mutable copy provided by caller)
     * @return updated tracks and track fusion events
     */
    public TrackFusionResult fuse(
            RadarInputMessage message,
            Map<TrackId, Track> currentTracks,
            long currentTime
    ) {
        List<TrackFusionEvent> events = new ArrayList<>();
        expireStaleTracks(currentTracks, currentTime, events);

        Position newPos = CoordinateTransformer.toCartesian(
            message.azimuth(),
            message.elevation(),
            message.slantRange(),
            message.timestamp(),
            0.95f  // high confidence from radar
        );

        Track directMatch = currentTracks.get(message.globalTrackId());
        Optional<TrackAssociation> bestMatch = directMatch != null
            ? Optional.of(new TrackAssociation(directMatch, 0.0))
            : findBestMatch(
                newPos,
                currentTracks.values(),
                associationGateDistance,
                message.timestamp()
            );

        if (bestMatch.isPresent()) {
            TrackId trackId = bestMatch.get().track().id();
            Track oldTrack = bestMatch.get().track();

            if (message.timestamp() <= oldTrack.position().timestamp()) {
                return new TrackFusionResult(currentTracks, events);
            }
            
            Track updatedTrack = updateTrack(oldTrack, newPos, currentTime);
            currentTracks.put(trackId, updatedTrack);
            
            events.add(TrackFusionEvent.trackUpdated(trackId));
        } else {
            TrackId newId = message.globalTrackId();
            Track newTrack = createTrack(newId, newPos, currentTime);
            currentTracks.put(newId, newTrack);
            
            events.add(TrackFusionEvent.trackCreated(newId));
        }

        return new TrackFusionResult(currentTracks, events);
    }

    private void expireStaleTracks(
            Map<TrackId, Track> currentTracks,
            long currentTime,
            List<TrackFusionEvent> events
    ) {
        List<TrackId> staleTracks = new ArrayList<>();
        for (var entry : currentTracks.entrySet()) {
            if (entry.getValue().isStale(currentTime, trackTTL)) {
                staleTracks.add(entry.getKey());
                events.add(TrackFusionEvent.trackExpired(entry.getKey()));
            }
        }
        staleTracks.forEach(currentTracks::remove);
    }

    /**
     * Find the best matching track for an observation.
     * 
     * @param newPos position of incoming observation
     * @param candidates existing tracks
     * @param gateDistance maximum allowed distance
     * @return optional association (track + distance)
     */
    private Optional<TrackAssociation> findBestMatch(
            Position newPos,
            Collection<Track> candidates,
            double gateDistance,
            long observationTime
    ) {
        TrackAssociation best = null;
        double bestDistance = gateDistance;

        for (Track candidate : candidates) {
            Position candidatePosition = candidate.estimatePositionAt(observationTime);
            double distance = newPos.distanceTo(candidatePosition);
            
            if (distance < bestDistance) {
                bestDistance = distance;
                best = new TrackAssociation(candidate, distance);
            }
        }

        return Optional.ofNullable(best);
    }

    /**
     * Update an existing track with a new observation.
     */
    private Track updateTrack(
            Track oldTrack,
            Position newPos,
            long currentTime
    ) {
        // Blend old position with new position (exponential moving average)
        double alpha = 0.5; // 50% weight on new measurement
        Position blendedPos = blendPositions(oldTrack.position(), newPos, alpha);

        // Compute new velocity
        Velocity newVel = Velocity.fromPositions(oldTrack.position(), newPos);
        Velocity smoothedVel = Velocity.smooth(oldTrack.velocity(), newVel, 0.3);

        // Increase confidence (up to 1.0)
        float newConfidence = Math.min(1.0f, oldTrack.confidence() + 0.05f);

        return new Track(
            oldTrack.id(),
            blendedPos,
            smoothedVel,
            newConfidence,
            oldTrack.updateCount() + 1,
            oldTrack.createdAt(),
            currentTime
        );
    }

    /**
     * Create a new track from an observation.
     */
    private Track createTrack(
            TrackId id,
            Position pos,
            long currentTime
    ) {
        return new Track(
            id,
            pos,
            Velocity.ZERO,  // initial velocity unknown
            0.5f,           // medium confidence for new track
            1,              // first update
            currentTime,
            currentTime
        );
    }

    /**
     * Blend two positions using exponential moving average.
     * Reduces jitter from noisy observations.
     */
    private Position blendPositions(Position old, Position fresh, double alpha) {
        double x = old.x() * (1 - alpha) + fresh.x() * alpha;
        double y = old.y() * (1 - alpha) + fresh.y() * alpha;
        double z = old.z() * (1 - alpha) + fresh.z() * alpha;
        float conf = (float) (old.confidence() * (1 - alpha) + fresh.confidence() * alpha);
        
        return new Position(x, y, z, fresh.timestamp(), conf);
    }

    /**
     * Result of a track fusion operation.
     */
    public record TrackFusionResult(
            Map<TrackId, Track> tracks,
            List<TrackFusionEvent> events
    ) {}

    /**
     * Internal: track association pairing.
     */
    private record TrackAssociation(Track track, double distance) {}

    /**
     * Events produced by track fusion logic.
     */
    public sealed interface TrackFusionEvent {
        record TrackCreated(TrackId id) implements TrackFusionEvent {}
        record TrackUpdated(TrackId id) implements TrackFusionEvent {}
        record TrackExpired(TrackId id) implements TrackFusionEvent {}

        static TrackFusionEvent trackCreated(TrackId id) {
            return new TrackCreated(id);
        }

        static TrackFusionEvent trackUpdated(TrackId id) {
            return new TrackUpdated(id);
        }

        static TrackFusionEvent trackExpired(TrackId id) {
            return new TrackExpired(id);
        }
    }
}
