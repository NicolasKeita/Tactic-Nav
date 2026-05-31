package com.tacticnav.protocol;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.zip.CRC32;

import static org.junit.jupiter.api.Assertions.*;

class RadarPacketCodecTest {

    @Test
    void serializeInto_shouldProduceValidPacket_forRealisticObservation() {
        RadarObservation observation = new RadarObservation(
            (short) 7,
            142.5f,
            12.3f,
            23450f,
            1700000000123L
        );

        byte[] buf = new byte[RadarPacketCodec.PACKET_SIZE];
        RadarPacketCodec.serializeInto(observation, buf);

        ByteBuffer bb = ByteBuffer.wrap(buf);

        assertEquals('R', bb.get(0));
        assertEquals('D', bb.get(1));
        assertEquals(7, bb.getShort(2));
        assertEquals(142.5f, bb.getFloat(4), 0.0001f);
        assertEquals(12.3f, bb.getFloat(8), 0.0001f);
        assertEquals(23450f, bb.getFloat(12), 0.001f);
        assertEquals(1700000000123L, bb.getLong(16));

        CRC32 reference = new CRC32();
        reference.update(buf, 0, RadarPacketCodec.CHECKSUM_OFFSET);
        assertEquals((int) reference.getValue(), bb.getInt(RadarPacketCodec.CHECKSUM_OFFSET));
    }

    @Test
    void parse_shouldRoundTripSerializedObservation() throws Exception {
        RadarObservation observation = new RadarObservation((short) 1, 270.0f, 5.0f, 10000f, 123456L);
        byte[] buf = new byte[RadarPacketCodec.PACKET_SIZE];

        RadarPacketCodec.serializeInto(observation, buf);

        assertEquals(observation, RadarPacketCodec.parse(buf, buf.length));
    }

    @Test
    void parse_shouldRejectInvalidHeader() {
        RadarObservation observation = new RadarObservation((short) 7, 90f, 0f, 1_000f, 100L);
        byte[] packet = new byte[RadarPacketCodec.PACKET_SIZE];
        RadarPacketCodec.serializeInto(observation, packet);
        packet[0] = 'X';

        assertThrows(ProtocolException.class, () -> RadarPacketCodec.parse(packet, packet.length));
    }
}
