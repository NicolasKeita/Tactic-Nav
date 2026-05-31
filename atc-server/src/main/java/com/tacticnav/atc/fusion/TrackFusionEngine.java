package com.tacticnav.atc.fusion;

import com.tacticnav.atc.domain.*;
import com.tacticnav.atc.network.CoordinateTransformer;
import java.util.*;

/**
 * Core track association and fusion logic.
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
 *   3. Prefer an active same-source track ID
 *   4. Find candidate existing tracks within a distance gate
 *   5. If one or more candidates exist: pick closest (nearest-neighbor)
 *   6. If no candidates exist: create a new track
 *   7. Ignore out-of-order observations for already matched tracks
 * 
 * Gate distance: 500m (tunable parameter)
 * Confidence decay: tracks lose confidence if not updated
 */
public final class TrackFusionEngine {
    
    // Gate parameters (tunable)
    private static final double ASSOCIATION_GATE_DISTANCE = 500.0;  // meters
    private static final long TRACK_TTL = 5000;  // milliseconds
    
    // Radar position (simplified: assumes single reference point)
    private final double radarLatitude;
    private final double radarLongitude;
    private final double radarAltitude;

    public TrackFusionEngine(double radarLat, double radarLon, double radarAlt) {
        this.radarLatitude = radarLat;
        this.radarLongitude = radarLon;
        this.radarAltitude = radarAlt;
    }

    /**
     * Process an incoming radar observation and update track state.
     * 
     * @param message normalized radar input
     * @param currentTracks existing tracks (mutable copy provided by caller)
     * @return (updated tracks, list of events for broadcast)
     */
    public FusionResult fuse(
            RadarInputMessage message,
            Map<TrackId, Track> currentTracks,
            long currentTime
    ) {
        List<FusionEvent> events = new ArrayList<>();
        expireStaleTracks(currentTracks, currentTime, events);

        Position newPos = CoordinateTransformer.toCartesian(
            message.azimuth(),
            message.elevation(),
            message.slantRange(),
            radarAltitude,
            message.timestamp(),
            0.95f  // high confidence from radar
        );

        Track directMatch = currentTracks.get(message.globalTrackId());
        Optional<TrackAssociation> bestMatch = directMatch != null
            ? Optional.of(new TrackAssociation(directMatch, 0.0))
            : findBestMatch(
                newPos,
                currentTracks.values(),
                ASSOCIATION_GATE_DISTANCE,
                message.timestamp()
            );

        if (bestMatch.isPresent()) {
            TrackId trackId = bestMatch.get().track().id();
            Track oldTrack = bestMatch.get().track();

            if (message.timestamp() <= oldTrack.position().timestamp()) {
                return new FusionResult(currentTracks, events);
            }
            
            Track updatedTrack = updateTrack(oldTrack, newPos, message.radarId(), currentTime);
            currentTracks.put(trackId, updatedTrack);
            
            events.add(FusionEvent.trackUpdated(trackId));
        } else {
            TrackId newId = message.globalTrackId();
            Track newTrack = createTrack(newId, newPos, message.radarId(), currentTime);
            currentTracks.put(newId, newTrack);
            
            events.add(FusionEvent.trackCreated(newId));
        }

        return new FusionResult(currentTracks, events);
    }

    private void expireStaleTracks(
            Map<TrackId, Track> currentTracks,
            long currentTime,
            List<FusionEvent> events
    ) {
        List<TrackId> staleTracks = new ArrayList<>();
        for (var entry : currentTracks.entrySet()) {
            if (entry.getValue().isStale(currentTime, TRACK_TTL)) {
                staleTracks.add(entry.getKey());
                events.add(FusionEvent.trackExpired(entry.getKey()));
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
            int radarId,
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

        long sourceRadarIds = oldTrack.sourceRadarIds() | radarSourceBit(radarId);

        return new Track(
            oldTrack.id(),
            blendedPos,
            smoothedVel,
            newConfidence,
            sourceRadarIds,
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
            int radarId,
            long currentTime
    ) {
        return new Track(
            id,
            pos,
            Velocity.ZERO,  // initial velocity unknown
            0.5f,           // medium confidence for new track
            radarSourceBit(radarId),
            1,              // first update
            currentTime,
            currentTime
        );
    }

    private long radarSourceBit(int radarId) {
        if (radarId < 0 || radarId >= Long.SIZE) {
            return 0L;
        }
        return 1L << radarId;
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
     * Result of a fusion operation.
     */
    public record FusionResult(
            Map<TrackId, Track> tracks,
            List<FusionEvent> events
    ) {}

    /**
     * Internal: track association pairing.
     */
    private record TrackAssociation(Track track, double distance) {}

    /**
     * Events produced by fusion logic (for logging/broadcast).
     */
    public sealed interface FusionEvent {
        record TrackCreated(TrackId id) implements FusionEvent {}
        record TrackUpdated(TrackId id) implements FusionEvent {}
        record TrackExpired(TrackId id) implements FusionEvent {}

        static FusionEvent trackCreated(TrackId id) {
            return new TrackCreated(id);
        }

        static FusionEvent trackUpdated(TrackId id) {
            return new TrackUpdated(id);
        }

        static FusionEvent trackExpired(TrackId id) {
            return new TrackExpired(id);
        }
    }
}
