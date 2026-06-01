package com.tacticnav.cockpit.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class TacticalSnapshot {
    private final long createdAtMillis;
    private final long sequenceNumber;
    private final List<TacticalTrack> tracks;
    private final List<NoFlyZone> zones;
    private final LinkStatus linkStatus;
    private final TacticalAlert alert;

    public TacticalSnapshot(
            long createdAtMillis,
            long sequenceNumber,
            List<TacticalTrack> tracks,
            List<NoFlyZone> zones,
            LinkStatus linkStatus,
            TacticalAlert alert
    ) {
        if (createdAtMillis < 0 || sequenceNumber < 0) {
            throw new IllegalArgumentException("timestamps and sequence numbers must be non-negative");
        }
        if (tracks == null || zones == null || linkStatus == null) {
            throw new IllegalArgumentException("tracks, zones, and linkStatus cannot be null");
        }
        this.createdAtMillis = createdAtMillis;
        this.sequenceNumber = sequenceNumber;
        this.tracks = Collections.unmodifiableList(new ArrayList<>(tracks));
        this.zones = Collections.unmodifiableList(new ArrayList<>(zones));
        this.linkStatus = linkStatus;
        this.alert = alert;
    }

    public static TacticalSnapshot empty(long nowMillis) {
        return new TacticalSnapshot(
                nowMillis,
                0L,
                Collections.<TacticalTrack>emptyList(),
                Collections.<NoFlyZone>emptyList(),
                LinkStatus.CONNECTING,
                null
        );
    }

    public long createdAtMillis() {
        return createdAtMillis;
    }

    public long sequenceNumber() {
        return sequenceNumber;
    }

    public List<TacticalTrack> tracks() {
        return tracks;
    }

    public List<NoFlyZone> zones() {
        return zones;
    }

    public LinkStatus linkStatus() {
        return linkStatus;
    }

    public TacticalAlert alert() {
        return alert;
    }

    public int trackCount() {
        return tracks.size();
    }

    public int alertCount() {
        return alert == null ? 0 : 1;
    }
}
