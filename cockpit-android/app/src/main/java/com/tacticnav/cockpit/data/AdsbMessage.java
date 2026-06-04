package com.tacticnav.cockpit.data;

/**
 * Simplified custom ADS-B message payload for the TACTIC-NAV simulation.
 * This packet is a fixed-size 112-byte UDP datagram that carries both
 * identification and bearing/range data for a single track.
 */
public final class AdsbMessage {
    private final short trackId;
    private final String emitterId;
    private final String callsign;
    private final float azimuth;
    private final float elevation;
    private final float slantRange;
    private final float heading;
    private final float groundSpeed;
    private final long timestamp;
    private final String stationId;

    public AdsbMessage(
            short trackId,
            String emitterId,
            String callsign,
            float azimuth,
            float elevation,
            float slantRange,
            float heading,
            float groundSpeed,
            long timestamp,
            String stationId
    ) {
        this.trackId = trackId;
        this.emitterId = emitterId;
        this.callsign = callsign;
        this.azimuth = azimuth;
        this.elevation = elevation;
        this.slantRange = slantRange;
        this.heading = heading;
        this.groundSpeed = groundSpeed;
        this.timestamp = timestamp;
        this.stationId = stationId;
    }

    public short trackId() { return trackId; }
    public String emitterId() { return emitterId; }
    public String callsign() { return callsign; }
    public float azimuth() { return azimuth; }
    public float elevation() { return elevation; }
    public float slantRange() { return slantRange; }
    public float heading() { return heading; }
    public float groundSpeed() { return groundSpeed; }
    public long timestamp() { return timestamp; }
    public String stationId() { return stationId; }
}