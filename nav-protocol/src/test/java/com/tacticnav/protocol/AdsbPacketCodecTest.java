package com.tacticnav.protocol;

import com.tacticnav.protocol.adsb.AdsbMessage;
import com.tacticnav.protocol.adsb.AdsbPacketCodec;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.zip.CRC32;

import static org.junit.jupiter.api.Assertions.*;

class AdsbPacketCodecTest {

    @Test
    void serializeInto_shouldProduceValid112BytePacket() {
        AdsbMessage message = new AdsbMessage(
            (short) 123,
            "ABC12345",
            "TEST123",
            180.0f,
            5.0f,
            12345.0f,
            90.0f,
            230.0f,
            1_700_000_000_000L,
            "GS-001"
        );

        byte[] buffer = new byte[AdsbPacketCodec.PACKET_SIZE];
        AdsbPacketCodec.serializeInto(message, buffer);

        assertEquals(AdsbPacketCodec.PACKET_SIZE, buffer.length);
        assertEquals('A', buffer[0]);
        assertEquals('D', buffer[1]);
        assertEquals('S', buffer[2]);
        assertEquals('B', buffer[3]);

        ByteBuffer bb = ByteBuffer.wrap(buffer);
        bb.position(8);
        assertEquals(123, bb.getShort());
        byte[] emitterBytes = new byte[8];
        bb.get(emitterBytes);
        assertEquals("ABC12345", new String(emitterBytes).trim());

        byte[] callsignBytes = new byte[16];
        bb.get(callsignBytes);
        assertEquals("TEST123", new String(callsignBytes).trim());

        assertEquals(180.0f, bb.getFloat(), 0.0001f);
        assertEquals(5.0f, bb.getFloat(), 0.0001f);
        assertEquals(12345.0f, bb.getFloat(), 0.001f);
        assertEquals(90.0f, bb.getFloat(), 0.0001f);
        assertEquals(230.0f, bb.getFloat(), 0.0001f);
        assertEquals(1_700_000_000_000L, bb.getLong());

        CRC32 crc = new CRC32();
        crc.update(buffer, 0, AdsbPacketCodec.CRC_OFFSET);
        bb.position(AdsbPacketCodec.CRC_OFFSET);
        assertEquals((int) crc.getValue(), bb.getInt());
    }

    @Test
    void parse_shouldRoundTripSerializedMessage() throws Exception {
        AdsbMessage message = new AdsbMessage(
            (short) 22,
            "ICAO0001",
            "FLIGHT1",
            1.0f,
            -2.0f,
            54321.0f,
            270.0f,
            150.0f,
            1_700_000_100_000L,
            "GROUND01"
        );

        byte[] buffer = new byte[AdsbPacketCodec.PACKET_SIZE];
        AdsbPacketCodec.serializeInto(message, buffer);

        assertEquals(message, AdsbPacketCodec.parse(buffer, buffer.length));
    }

    @Test
    void parse_shouldRejectInvalidHeader() {
        AdsbMessage message = new AdsbMessage(
            (short) 22,
            "ICAO0001",
            "FLIGHT1",
            1.0f,
            -2.0f,
            54321.0f,
            270.0f,
            150.0f,
            1_700_000_100_000L,
            "GROUND01"
        );

        byte[] buffer = new byte[AdsbPacketCodec.PACKET_SIZE];
        AdsbPacketCodec.serializeInto(message, buffer);
        buffer[0] = 'X';

        assertThrows(ProtocolException.class, () -> AdsbPacketCodec.parse(buffer, buffer.length));
    }
}
