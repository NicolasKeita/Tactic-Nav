package com.tacticnav.radar;

import java.nio.ByteBuffer;

public final class PacketSerializer {
    private PacketSerializer() {}

    /**
     * Serialize a Track into a fixed 32-byte packet layout:
     * [0-1]   HEADER = 'R''D' (2 bytes)
     * [2-3]   TRACK_ID (short)
     * [4-7]   LAT (float)
     * [8-11]  LON (float)
     * [12-15] ALT (float)
     * [16-19] SPEED (float)
     * [20-27] TIMESTAMP (long)
     * [28-31] CRC32 over first 28 bytes (int)
     */
    public static void serializeInto(Track track, byte[] buffer) {
        if (buffer == null || buffer.length != 32) {
            throw new IllegalArgumentException("buffer must be 32 bytes");
        }

        ByteBuffer bb = ByteBuffer.wrap(buffer);
        // HEADER
        bb.put((byte) 'R');
        bb.put((byte) 'D');
        // TRACK_ID
        bb.putShort(track.trackId());
        // LAT/LON/ALT/SPEED
        bb.putFloat(track.lat());
        bb.putFloat(track.lon());
        bb.putFloat(track.alt());
        bb.putFloat(track.speed());
        // TIMESTAMP
        bb.putLong(track.timestamp());

        // compute CRC32 over first 28 bytes
        int crc = Crc32Util.computeCrc32(buffer, 0, 28);
        bb.putInt(crc);
    }
}
