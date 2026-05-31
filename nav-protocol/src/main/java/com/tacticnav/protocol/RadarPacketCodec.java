package com.tacticnav.protocol;

import java.nio.ByteBuffer;

/**
 * Codec for the fixed-size radar UDP packet.
 *
 * Packet format, big-endian:
 * [0-1]   HEADER = 'R''D'
 * [2-3]   TRACK_ID (short)
 * [4-7]   AZIMUTH (float)
 * [8-11]  ELEVATION (float)
 * [12-15] SLANT_RANGE (float)
 * [16-23] TIMESTAMP (long)
 * [24-27] CRC32 over first 24 bytes
 */
public final class RadarPacketCodec {
    public static final int PACKET_SIZE = 28;
    public static final int CHECKSUM_OFFSET = 24;

    private static final byte HEADER_BYTE_0 = 'R';
    private static final byte HEADER_BYTE_1 = 'D';

    private RadarPacketCodec() {}

    public static void serializeInto(RadarObservation observation, byte[] buffer) {
        if (buffer == null || buffer.length != PACKET_SIZE) {
            throw new IllegalArgumentException("buffer must be 28 bytes");
        }
        ByteBuffer bb = ByteBuffer.wrap(buffer);
        bb.put(HEADER_BYTE_0);
        bb.put(HEADER_BYTE_1);
        bb.putShort(observation.trackId());
        bb.putFloat(observation.azimuth());
        bb.putFloat(observation.elevation());
        bb.putFloat(observation.slantRange());
        bb.putLong(observation.timestamp());
        bb.putInt(Crc32Util.computeCrc32(buffer, 0, CHECKSUM_OFFSET));
    }

    public static RadarObservation parse(byte[] packetData, int length) throws ProtocolException {
        if (length != PACKET_SIZE) {
            throw new ProtocolException("Invalid packet size: " + length + ", expected " + PACKET_SIZE);
        }
        if (packetData[0] != HEADER_BYTE_0 || packetData[1] != HEADER_BYTE_1) {
            throw new ProtocolException(
                "Invalid header: got '" + (char) packetData[0] + (char) packetData[1] + "', expected 'RD'"
            );
        }

        int expectedCrc = Crc32Util.computeCrc32(packetData, 0, CHECKSUM_OFFSET);
        int actualCrc = ByteBuffer.wrap(packetData).getInt(CHECKSUM_OFFSET);
        if (expectedCrc != actualCrc) {
            throw new ProtocolException(
                "CRC32 mismatch: expected 0x" + Integer.toHexString(expectedCrc) +
                ", got 0x" + Integer.toHexString(actualCrc)
            );
        }

        ByteBuffer buffer = ByteBuffer.wrap(packetData);
        buffer.get();
        buffer.get();
        short trackId = buffer.getShort();
        float azimuth = buffer.getFloat();
        float elevation = buffer.getFloat();
        float slantRange = buffer.getFloat();
        long timestamp = buffer.getLong();

        return new RadarObservation(trackId, azimuth, elevation, slantRange, timestamp);
    }
}
