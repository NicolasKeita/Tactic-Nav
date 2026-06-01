package com.tacticnav.cockpit.data;

import com.tacticnav.cockpit.domain.TacticalSnapshot;

import org.junit.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public final class TrackDatagramDecoderTest {
    @Test
    public void decodeParsesSingleTrackPacket() throws Exception {
        byte[] packet = oneTrackPacket();

        TacticalSnapshot snapshot = new TrackDatagramDecoder().decode(packet, packet.length, Collections.emptyList());

        assertEquals(9L, snapshot.sequenceNumber());
        assertEquals(1, snapshot.trackCount());
        assertEquals("track-7", snapshot.tracks().get(0).id());
        assertEquals(43.9001, snapshot.tracks().get(0).position().latitude(), 0.00001);
        assertEquals(-0.5002, snapshot.tracks().get(0).position().longitude(), 0.00001);
        assertEquals(12_500, snapshot.tracks().get(0).altitudeFt());
    }

    @Test
    public void decodeRejectsInvalidMagic() {
        byte[] packet = oneTrackPacket();
        packet[0] = 0x00;

        assertThrows(
                TrackDatagramDecoder.DecodeException.class,
                () -> new TrackDatagramDecoder().decode(packet, packet.length, Collections.emptyList())
        );
    }

    @Test
    public void decodeRejectsTruncatedPacket() {
        byte[] packet = oneTrackPacket();

        assertThrows(
                TrackDatagramDecoder.DecodeException.class,
                () -> new TrackDatagramDecoder().decode(packet, packet.length - 1, Collections.emptyList())
        );
    }

    private static byte[] oneTrackPacket() {
        ByteBuffer buffer = ByteBuffer.allocate(TrackDatagramDecoder.HEADER_BYTES + TrackDatagramDecoder.TRACK_BYTES)
                .order(ByteOrder.BIG_ENDIAN);
        buffer.putInt(TrackDatagramDecoder.MAGIC);
        buffer.putLong(9L);
        buffer.putLong(123_456L);
        buffer.putShort((short) 1);
        buffer.putShort((short) 7);
        buffer.putDouble(43.9001);
        buffer.putDouble(-0.5002);
        buffer.putInt(12_500);
        buffer.putFloat(91.0f);
        buffer.putFloat(320.0f);
        buffer.putFloat(-125.0f);
        buffer.putFloat(0.87f);
        return buffer.array();
    }
}
