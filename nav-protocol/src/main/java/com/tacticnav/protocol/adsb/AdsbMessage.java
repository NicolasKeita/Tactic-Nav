package com.tacticnav.protocol.adsb;

/**
 * Simplified custom ADS-B message payload for the TACTIC-NAV simulation.
 * This packet is a fixed-size 112-byte UDP datagram that carries both
 * broadcast position data and optional metadata useful for ground station relay.
 */
public record AdsbMessage(
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
    public AdsbMessage {
        if (emitterId == null || emitterId.isBlank()) {
            throw new IllegalArgumentException("emitterId must not be blank");
        }
        if (callsign == null || callsign.isBlank()) {
            throw new IllegalArgumentException("callsign must not be blank");
        }
        if (stationId == null || stationId.isBlank()) {
            throw new IllegalArgumentException("stationId must not be blank");
        }
    }
}
