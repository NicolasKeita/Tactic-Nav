package com.tacticnav.cockpit.processing;

import com.tacticnav.cockpit.domain.AlertSeverity;
import com.tacticnav.cockpit.domain.LinkStatus;
import com.tacticnav.cockpit.domain.NoFlyZone;
import com.tacticnav.cockpit.domain.TacticalAlert;
import com.tacticnav.cockpit.domain.TacticalSnapshot;
import com.tacticnav.cockpit.domain.TacticalTrack;
import com.tacticnav.cockpit.domain.TrackStatus;
import com.tacticnav.cockpit.geo.GeoMath;
import com.tacticnav.cockpit.time.Clock;

import java.util.ArrayList;
import java.util.List;

public final class SituationProcessor {
    private static final long STALE_TRACK_AFTER_MILLIS = 2_500L;
    private static final long DEGRADED_LINK_AFTER_MILLIS = 1_500L;
    private static final long LOST_LINK_AFTER_MILLIS = 3_500L;
    private static final double WARNING_DISTANCE_METERS = 3.0 * GeoMath.METERS_PER_NAUTICAL_MILE;

    private final Clock clock;

    public SituationProcessor(Clock clock) {
        if (clock == null) {
            throw new IllegalArgumentException("clock cannot be null");
        }
        this.clock = clock;
    }

    public TacticalSnapshot process(TacticalSnapshot rawSnapshot) {
        long now = clock.nowMillis();
        List<TacticalTrack> processedTracks = new ArrayList<>(rawSnapshot.tracks().size());
        TacticalAlert alert = null;

        for (TacticalTrack track : rawSnapshot.tracks()) {
            Classification classification = classify(track, rawSnapshot.zones(), now);
            processedTracks.add(track.withStatus(classification.status));
            if (alert == null && classification.zone != null && classification.status == TrackStatus.INTRUDER) {
                alert = new TacticalAlert(
                        AlertSeverity.CRITICAL,
                        track.id(),
                        classification.zone.id(),
                        track.callsign() + " entering " + classification.zone.name(),
                        now
                );
            }
        }

        return new TacticalSnapshot(
                rawSnapshot.createdAtMillis(),
                rawSnapshot.sequenceNumber(),
                processedTracks,
                rawSnapshot.zones(),
                deriveLinkStatus(rawSnapshot, now),
                alert
        );
    }

    private Classification classify(TacticalTrack track, List<NoFlyZone> zones, long nowMillis) {
        if (nowMillis - track.lastUpdatedMillis() > STALE_TRACK_AFTER_MILLIS) {
            return new Classification(TrackStatus.STALE, null);
        }

        NoFlyZone warningZone = null;
        for (NoFlyZone zone : zones) {
            if (!zone.containsAltitude(track.altitudeFt())) {
                continue;
            }
            if (GeoMath.pointInPolygon(track.position(), zone.vertices())) {
                return new Classification(TrackStatus.INTRUDER, zone);
            }
            if (warningZone == null
                    && GeoMath.distanceToPolygonMeters(track.position(), zone.vertices()) <= WARNING_DISTANCE_METERS) {
                warningZone = zone;
            }
        }

        return warningZone == null
                ? new Classification(TrackStatus.NORMAL, null)
                : new Classification(TrackStatus.WARNING, warningZone);
    }

    private LinkStatus deriveLinkStatus(TacticalSnapshot snapshot, long nowMillis) {
        if (snapshot.linkStatus() == LinkStatus.SIMULATED || snapshot.linkStatus() == LinkStatus.CONNECTING) {
            return snapshot.linkStatus();
        }

        long ageMillis = nowMillis - snapshot.createdAtMillis();
        if (ageMillis > LOST_LINK_AFTER_MILLIS) {
            return LinkStatus.LOST;
        }
        if (ageMillis > DEGRADED_LINK_AFTER_MILLIS) {
            return LinkStatus.DEGRADED;
        }
        return LinkStatus.LIVE;
    }

    private static final class Classification {
        private final TrackStatus status;
        private final NoFlyZone zone;

        private Classification(TrackStatus status, NoFlyZone zone) {
            this.status = status;
            this.zone = zone;
        }
    }
}
