package com.tacticnav.radar;

import java.nio.ByteBuffer;

/**
 * Serializes Track data into a fixed 28-byte packet layout:
 * [0-1]   HEADER = 'R''D' (2 bytes)
 * [2-3]   TRACK_ID (short)
 * [4-7]   AZIMUTH (float, degrees)
 * [8-11]  ELEVATION (float, degrees)
 * [12-15] SLANT_RANGE (float, meters)
 * [16-23] TIMESTAMP (long)
 * [24-27] CRC32 over first 24 bytes (int)
 */
public final class PacketSerializer {
    private PacketSerializer() {}

    public static void serializeInto(Track track, byte[] buffer) {
        if (buffer == null || buffer.length != 28) {
            throw new IllegalArgumentException("buffer must be 28 bytes");
        }
        ByteBuffer bb = ByteBuffer.wrap(buffer);
        bb.put((byte) 'R');
        bb.put((byte) 'D');
        bb.putShort(track.trackId());
        bb.putFloat(track.azimuth());
        bb.putFloat(track.elevation());
        bb.putFloat(track.slantRange());
        bb.putLong(track.timestamp());
        int crc = Crc32Util.computeCrc32(buffer, 0, 24);
        bb.putInt(crc);
    }
}