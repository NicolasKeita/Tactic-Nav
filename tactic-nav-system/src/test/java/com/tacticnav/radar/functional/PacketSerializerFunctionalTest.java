package com.tacticnav.radar.functional;

import com.tacticnav.radar.PacketSerializer;
import com.tacticnav.radar.Track;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.zip.CRC32;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Functional (integration) test for PacketSerializer.
 * Exercises the full pipeline: Track creation → serialization → binary layout → CRC32 integrity.
 */
class PacketSerializerFunctionalTest {

    /**
     * Full round-trip: create a Track, serialize it, and verify every field
     * and the trailer CRC32 match exactly.
     */
    @Test
    void serializeInto_shouldProduceValidPacket_forRealisticTrack() {
        Track track = new Track(
                (short) 7,
                142.5f,       // azimuth (degrees)
                12.3f,        // elevation (degrees)
                23450f,       // slant range (meters)
                1700000000123L
        );

        // When — serialize into a 28-byte buffer
        byte[] buf = new byte[28];
        PacketSerializer.serializeInto(track, buf);

        // Then — verify the full binary envelope
        ByteBuffer bb = ByteBuffer.wrap(buf);

        // Header
        assertEquals('R', bb.get(0),  "Header byte 0 must be 'R'");
        assertEquals('D', bb.get(1),  "Header byte 1 must be 'D'");

        // Track ID
        assertEquals(7, bb.getShort(2));

        // Spherical coordinates (with tolerance for float representation)
        assertEquals(142.5f,  bb.getFloat(4),  0.0001f, "Azimuth mismatch");
        assertEquals(12.3f,   bb.getFloat(8),  0.0001f, "Elevation mismatch");
        assertEquals(23450f,  bb.getFloat(12), 0.001f,  "Slant range mismatch");

        // Timestamp
        assertEquals(1700000000123L, bb.getLong(16));

        // CRC32 — must cover exactly the first 24 bytes
        int storedCrc = bb.getInt(24);
        CRC32 reference = new CRC32();
        reference.update(buf, 0, 24);
        int expectedCrc = (int) reference.getValue();

        assertEquals(expectedCrc, storedCrc, "CRC32 integrity check failed");
    }

    /**
     * Two identical tracks must produce byte-identical packets
     * (determinism of the serialization).
     */
    @Test
    void serializeInto_shouldBeDeterministic() {
        Track track = new Track((short) 1, 270.0f, 5.0f, 10000f, 123456L);

        byte[] buf1 = new byte[28];
        byte[] buf2 = new byte[28];

        PacketSerializer.serializeInto(track, buf1);
        PacketSerializer.serializeInto(track, buf2);

        assertArrayEquals(buf1, buf2, "Two serializations of the same track must be identical");
    }
}