package com.tacticnav.atc.network;

import com.tacticnav.atc.domain.RadarInputMessage;
import com.tacticnav.radar.Crc32Util;

import java.nio.ByteBuffer;

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
    private static final int PACKET_SIZE = 28;
    private static final byte HEADER_BYTE_0 = 'R';
    private static final byte HEADER_BYTE_1 = 'D';
    
    private final int radarId;
    private final ByteBuffer buffer = ByteBuffer.allocate(PACKET_SIZE);

    public RadarPacketParser(int radarId) {
        this.radarId = radarId;
    }

    /**
     * Parse a UDP packet into a RadarInputMessage.
     * 
     * @param packetData raw bytes from UDP
     * @param length number of valid bytes in packetData
     * @return normalized radar input message
     * @throws ParseException if the packet is malformed or outside accepted ranges
     */
    public RadarInputMessage parse(byte[] packetData, int length) throws ParseException {
        if (length != PACKET_SIZE) {
            throw new ParseException("Invalid packet size: " + length + ", expected " + PACKET_SIZE);
        }

        if (packetData[0] != HEADER_BYTE_0 || packetData[1] != HEADER_BYTE_1) {
            throw new ParseException(
                "Invalid header: got '" + (char) packetData[0] + (char) packetData[1] + "', expected 'RD'"
            );
        }

        int expectedCrc = Crc32Util.computeCrc32(packetData, 0, 24);
        int actualCrc = ByteBuffer.wrap(packetData).getInt(24);
        if (expectedCrc != actualCrc) {
            throw new ParseException(
                "CRC32 mismatch: expected 0x" + Integer.toHexString(expectedCrc) +
                ", got 0x" + Integer.toHexString(actualCrc)
            );
        }

        buffer.clear();
        buffer.put(packetData, 0, PACKET_SIZE);
        buffer.flip();

        buffer.get();
        buffer.get();
        short trackId = buffer.getShort();
        float azimuth = buffer.getFloat();
        float elevation = buffer.getFloat();
        float slantRange = buffer.getFloat();
        long timestamp = buffer.getLong();

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
            radarId,
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
