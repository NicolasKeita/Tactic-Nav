package com.tacticnav.protocol.adsb;

import com.tacticnav.protocol.Crc32Util;
import com.tacticnav.protocol.ProtocolException;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * Fixed-size custom ADS-B packet codec for simulation.
 *
 * Packet layout (112 bytes):
 * [0-3]   HEADER = 'A''D''S''B'
 * [4]     VERSION = 1
 * [5-7]   RESERVED
 * [8-9]   TRACK_ID (short)
 * [10-17] EMITTER_ID (8-byte ASCII, padded with NUL)
 * [18-33] CALLSIGN (16-byte ASCII, padded with NUL)
 * [34-37] AZIMUTH (float)
 * [38-41] ELEVATION (float)
 * [42-45] SLANT_RANGE (float)
 * [46-49] HEADING (float)
 * [50-53] GROUND_SPEED (float)
 * [54-61] TIMESTAMP (long)
 * [62-77] STATION_ID (16-byte ASCII, padded with NUL)
 * [78-107] RESERVED (30 bytes)
 * [108-111] CRC32 over first 108 bytes
 */
public final class AdsbPacketCodec {
    public static final int PACKET_SIZE = 112;
    public static final int HEADER_SIZE = 4;
    public static final int CRC_OFFSET = 108;
    private static final byte[] HEADER = {'A', 'D', 'S', 'B'};
    private static final byte VERSION = 1;

    private AdsbPacketCodec() {}

    public static void serializeInto(AdsbMessage message, byte[] buffer) {
        if (buffer == null || buffer.length != PACKET_SIZE) {
            throw new IllegalArgumentException("buffer must be exactly " + PACKET_SIZE + " bytes");
        }

        ByteBuffer bb = ByteBuffer.wrap(buffer);
        bb.put(HEADER);
        bb.put(VERSION);
        bb.put(new byte[3]);
        bb.putShort(message.trackId());
        putFixedAscii(bb, message.emitterId(), 8);
        putFixedAscii(bb, message.callsign(), 16);
        bb.putFloat(message.azimuth());
        bb.putFloat(message.elevation());
        bb.putFloat(message.slantRange());
        bb.putFloat(message.heading());
        bb.putFloat(message.groundSpeed());
        bb.putLong(message.timestamp());
        putFixedAscii(bb, message.stationId(), 16);
        bb.put(new byte[30]);

        int crc = Crc32Util.computeCrc32(buffer, 0, CRC_OFFSET);
        bb.putInt(crc);
    }

    public static AdsbMessage parse(byte[] packetData, int length) throws ProtocolException {
        if (length != PACKET_SIZE) {
            throw new ProtocolException("Invalid ADS-B packet size: " + length + ", expected " + PACKET_SIZE);
        }

        if (packetData[0] != HEADER[0] || packetData[1] != HEADER[1] || packetData[2] != HEADER[2] || packetData[3] != HEADER[3]) {
            throw new ProtocolException("Invalid ADS-B packet header");
        }

        int expectedCrc = Crc32Util.computeCrc32(packetData, 0, CRC_OFFSET);
        int actualCrc = ByteBuffer.wrap(packetData).getInt(CRC_OFFSET);
        if (expectedCrc != actualCrc) {
            throw new ProtocolException("ADS-B CRC32 mismatch: expected 0x" + Integer.toHexString(expectedCrc) + ", got 0x" + Integer.toHexString(actualCrc));
        }

        ByteBuffer bb = ByteBuffer.wrap(packetData);
        bb.position(4);
        byte version = bb.get();
        if (version != VERSION) {
            throw new ProtocolException("Unsupported ADS-B version: " + version);
        }
        bb.position(8);

        short trackId = bb.getShort();
        String emitterId = readFixedAscii(bb, 8);
        String callsign = readFixedAscii(bb, 16);
        float azimuth = bb.getFloat();
        float elevation = bb.getFloat();
        float slantRange = bb.getFloat();
        float heading = bb.getFloat();
        float groundSpeed = bb.getFloat();
        long timestamp = bb.getLong();
        String stationId = readFixedAscii(bb, 16);

        return new AdsbMessage(
            trackId,
            emitterId,
            callsign,
            azimuth,
            elevation,
            slantRange,
            heading,
            groundSpeed,
            timestamp,
            stationId
        );
    }

    private static void putFixedAscii(ByteBuffer bb, String value, int length) {
        byte[] bytes = value.getBytes(StandardCharsets.US_ASCII);
        if (bytes.length > length) {
            throw new IllegalArgumentException("Field too long: " + value + " (max " + length + " bytes)");
        }
        bb.put(bytes);
        for (int i = bytes.length; i < length; i++) {
            bb.put((byte) 0);
        }
    }

    private static String readFixedAscii(ByteBuffer bb, int length) {
        byte[] bytes = new byte[length];
        bb.get(bytes);
        int end = 0;
        while (end < bytes.length && bytes[end] != 0) {
            end++;
        }
        return new String(bytes, 0, end, StandardCharsets.US_ASCII);
    }
}
