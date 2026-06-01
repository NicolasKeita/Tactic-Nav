package com.tacticnav.cockpit.domain;

public final class TacticalAlert {
    private final AlertSeverity severity;
    private final String trackId;
    private final String zoneId;
    private final String message;
    private final long raisedAtMillis;

    public TacticalAlert(
            AlertSeverity severity,
            String trackId,
            String zoneId,
            String message,
            long raisedAtMillis
    ) {
        if (severity == null) {
            throw new IllegalArgumentException("severity cannot be null");
        }
        if (isBlank(trackId) || isBlank(zoneId) || isBlank(message)) {
            throw new IllegalArgumentException("trackId, zoneId, and message are required");
        }
        if (raisedAtMillis < 0) {
            throw new IllegalArgumentException("raisedAtMillis must be non-negative");
        }
        this.severity = severity;
        this.trackId = trackId;
        this.zoneId = zoneId;
        this.message = message;
        this.raisedAtMillis = raisedAtMillis;
    }

    public AlertSeverity severity() {
        return severity;
    }

    public String trackId() {
        return trackId;
    }

    public String zoneId() {
        return zoneId;
    }

    public String message() {
        return message;
    }

    public long raisedAtMillis() {
        return raisedAtMillis;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
