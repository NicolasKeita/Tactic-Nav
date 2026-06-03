package com.tacticnav.atc.unit;

import com.tacticnav.atc.domain.RadarInputMessage;
import com.tacticnav.atc.network.AdsbPacketParser;
import com.tacticnav.protocol.adsb.AdsbMessage;
import com.tacticnav.protocol.adsb.AdsbPacketCodec;
import com.tacticnav.protocol.ProtocolException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AdsbPacketParserTest {

    @Test
    void parse_shouldConvertAdsbPacketIntoNormalizedRadarInputMessage() throws Exception {
        AdsbMessage message = new AdsbMessage(
            (short) 33,
            "ABC12345",
            "TEST123",
            45.0f,
            1.5f,
            15000.0f,
            90.0f,
            220.0f,
            1_700_000_100_000L,
            "GS-001"
        );

        byte[] packet = new byte[AdsbPacketCodec.PACKET_SIZE];
        AdsbPacketCodec.serializeInto(message, packet);

        AdsbPacketParser parser = new AdsbPacketParser();
        RadarInputMessage input = parser.parse(packet, packet.length);

        assertEquals(33, input.trackId());
        assertEquals(45.0f, input.azimuth(), 0.0001f);
        assertEquals(1.5f, input.elevation(), 0.0001f);
        assertEquals(15000.0f, input.slantRange(), 0.001f);
        assertEquals(1_700_000_100_000L, input.timestamp());
    }

    @Test
    void parse_shouldRejectTamperedPacket() throws Exception {
        AdsbMessage message = new AdsbMessage(
            (short) 33,
            "ABC12345",
            "TEST123",
            45.0f,
            1.5f,
            15000.0f,
            90.0f,
            220.0f,
            1_700_000_100_000L,
            "GS-001"
        );

        byte[] packet = new byte[AdsbPacketCodec.PACKET_SIZE];
        AdsbPacketCodec.serializeInto(message, packet);
        packet[0] = 'X';

        AdsbPacketParser parser = new AdsbPacketParser();
        assertThrows(AdsbPacketParser.ParseException.class, () -> parser.parse(packet, packet.length));
    }
}
