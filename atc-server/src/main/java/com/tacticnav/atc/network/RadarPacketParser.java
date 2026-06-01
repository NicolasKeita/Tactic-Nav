package com.tacticnav.atc.network;

import com.tacticnav.atc.domain.RadarInputMessage;
import com.tacticnav.protocol.ProtocolException;
import com.tacticnav.protocol.RadarObservation;
import com.tacticnav.protocol.RadarPacketCodec;

/**
 * Parses raw UDP radar packets into normalized RadarInputMessage records.
 * 
 * Packet format (28 bytes, big-endian):
 *   [0-1]   HEADER = 'R''D' (2 bytes)
 *   [2-3]   TRACK_ID (short)
 *   [4-7]   AZIMUTH (float, degrees)
 *   [8-11]  ELEVATION (float, degrees)
 *   [12-15] SLANT_RANGE (float, meters)
 *   [16-23] TIMESTAMP (long)
 *   [24-27] CRC32 over first 24 bytes (int)
 * 
 * Invalid packets are reported as ParseException and discarded by the listener.
 */
public final class RadarPacketParser {
    /**
     * Parse a UDP packet into a RadarInputMessage.
     * 
     * @param packetData raw bytes from UDP
     * @param length number of valid bytes in packetData
     * @return normalized radar observation
     * @throws ParseException if the packet is malformed or outside accepted ranges
     */
    public RadarInputMessage parse(byte[] packetData, int length) throws ParseException {
        RadarObservation observation;
        try {
            observation = RadarPacketCodec.parse(packetData, length);
        } catch (ProtocolException e) {
            throw new ParseException(e.getMessage());
        }

        short trackId = observation.trackId();
        float azimuth = observation.azimuth();
        float elevation = observation.elevation();
        float slantRange = observation.slantRange();
        long timestamp = observation.timestamp();

        if (!Float.isFinite(azimuth) || azimuth < 0f || azimuth > 360f) {
            throw new ParseException("Invalid azimuth: " + azimuth);
        }
        if (!Float.isFinite(elevation) || elevation < -90f || elevation > 90f) {
            throw new ParseException("Invalid elevation: " + elevation);
        }
        if (!Float.isFinite(slantRange) || slantRange < 0f) {
            throw new ParseException("Invalid slantRange: " + slantRange);
        }
        if (timestamp < 0) {
            throw new ParseException("Invalid timestamp: " + timestamp);
        }

        return new RadarInputMessage(
            trackId,
            azimuth,
            elevation,
            slantRange,
            timestamp
        );
    }

    /**
     * Represents a parsing error for a single UDP datagram.
     */
    public static final class ParseException extends Exception {
        public ParseException(String message) {
            super(message);
        }
    }
}
