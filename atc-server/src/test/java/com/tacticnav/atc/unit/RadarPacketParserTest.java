package com.tacticnav.atc.unit;

import com.tacticnav.atc.domain.RadarInputMessage;
import com.tacticnav.atc.network.RadarPacketParser;
import com.tacticnav.protocol.RadarObservation;
import com.tacticnav.protocol.RadarPacketCodec;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RadarPacketParserTest {

    @Test
    void parse_shouldReturnNormalizedMessage_forValidPacket() throws Exception {
        byte[] packet = new byte[RadarPacketCodec.PACKET_SIZE];
        RadarPacketCodec.serializeInto(
            new RadarObservation((short) 42, 180.5f, 8.25f, 12_000f, 1_700_000_000_000L),
            packet
        );

        RadarPacketParser parser = new RadarPacketParser();

        RadarInputMessage message = parser.parse(packet, packet.length);

        assertEquals(42, message.trackId());
        assertEquals(180.5f, message.azimuth(), 0.0001f);
        assertEquals(8.25f, message.elevation(), 0.0001f);
        assertEquals(12_000f, message.slantRange(), 0.001f);
        assertEquals(1_700_000_000_000L, message.timestamp());
    }

    @Test
    void parse_shouldRejectInvalidHeader() {
        byte[] packet = new byte[RadarPacketCodec.PACKET_SIZE];
        RadarPacketCodec.serializeInto(
            new RadarObservation((short) 7, 90f, 0f, 1_000f, 100L),
            packet
        );
        packet[0] = 'X';

        RadarPacketParser parser = new RadarPacketParser();

        assertThrows(RadarPacketParser.ParseException.class, () -> parser.parse(packet, packet.length));
    }
}
