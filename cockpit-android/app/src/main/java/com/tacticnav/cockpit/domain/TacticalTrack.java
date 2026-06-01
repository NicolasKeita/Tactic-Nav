package com.tacticnav.cockpit.domain;

public final class TacticalTrack {
    private final String id;
    private final String callsign;
    private final GeoPoint position;
    private final int altitudeFt;
    private final float headingDeg;
    private final float groundSpeedKt;
    private final float verticalSpeedFpm;
    private final float confidence;
    private final long lastUpdatedMillis;
    private final TrackStatus status;

    public TacticalTrack(
            String id,
            String callsign,
            GeoPoint position,
            int altitudeFt,
            float headingDeg,
            float groundSpeedKt,
            float verticalSpeedFpm,
            float confidence,
            long lastUpdatedMillis,
            TrackStatus status
    ) {
        if (isBlank(id) || isBlank(callsign)) {
            throw new IllegalArgumentException("id and callsign are required");
        }
        if (position == null || status == null) {
            throw new IllegalArgumentException("position and status cannot be null");
        }
        if (!Float.isFinite(headingDeg) || !Float.isFinite(groundSpeedKt) || !Float.isFinite(verticalSpeedFpm)) {
            throw new IllegalArgumentException("track vectors must be finite");
        }
        if (!Float.isFinite(confidence) || confidence < 0.0f || confidence > 1.0f) {
            throw new IllegalArgumentException("confidence must be in [0, 1]");
        }
        if (lastUpdatedMillis < 0) {
            throw new IllegalArgumentException("lastUpdatedMillis must be non-negative");
        }
        this.id = id;
        this.callsign = callsign;
        this.position = position;
        this.altitudeFt = altitudeFt;
        this.headingDeg = normalizeHeading(headingDeg);
        this.groundSpeedKt = Math.max(0.0f, groundSpeedKt);
        this.verticalSpeedFpm = verticalSpeedFpm;
        this.confidence = confidence;
        this.lastUpdatedMillis = lastUpdatedMillis;
        this.status = status;
    }

    public String id() {
        return id;
    }

    public String callsign() {
        return callsign;
    }

    public GeoPoint position() {
        return position;
    }

    public int altitudeFt() {
        return altitudeFt;
    }

    public float headingDeg() {
        return headingDeg;
    }

    public float groundSpeedKt() {
        return groundSpeedKt;
    }

    public float verticalSpeedFpm() {
        return verticalSpeedFpm;
    }

    public float confidence() {
        return confidence;
    }

    public long lastUpdatedMillis() {
        return lastUpdatedMillis;
    }

    public TrackStatus status() {
        return status;
    }

    public TacticalTrack withStatus(TrackStatus nextStatus) {
        return new TacticalTrack(
                id,
                callsign,
                position,
                altitudeFt,
                headingDeg,
                groundSpeedKt,
                verticalSpeedFpm,
                confidence,
                lastUpdatedMillis,
                nextStatus
        );
    }

    private static float normalizeHeading(float headingDeg) {
        float normalized = headingDeg % 360.0f;
        return normalized < 0.0f ? normalized + 360.0f : normalized;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
