package com.tacticnav.cockpit.embedded;

import com.tacticnav.cockpit.data.SimulatedTrackGenerator;
import com.tacticnav.cockpit.data.TrackDatagramDecoder;
import com.tacticnav.cockpit.domain.LinkStatus;
import com.tacticnav.cockpit.domain.TacticalSnapshot;
import com.tacticnav.cockpit.processing.SituationProcessor;
import com.tacticnav.cockpit.time.Clock;

import org.junit.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class EmbeddedConstraintBudgetTest {
    private static final int COMMON_LOCAL_MTU_PAYLOAD_BYTES = 1472;
    private static final int DECODER_STRESS_ITERATIONS = 2_000;
    private static final int SIMULATED_MISSION_ITERATIONS = 5_000;

    @Test
    public void cockpitDatagramBudgetFitsCommonLocalMtuPayload() {
        int expectedMaxPacketBytes = TrackDatagramDecoder.HEADER_BYTES
                + TrackDatagramDecoder.MAX_TRACKS * TrackDatagramDecoder.TRACK_BYTES;

        assertEquals(expectedMaxPacketBytes, TrackDatagramDecoder.MAX_PACKET_BYTES);
        assertTrue(TrackDatagramDecoder.MAX_PACKET_BYTES <= COMMON_LOCAL_MTU_PAYLOAD_BYTES);
    }

    @Test
    public void decoderStressKeepsMaximumSnapshotBounded() throws Exception {
        TrackDatagramDecoder decoder = new TrackDatagramDecoder();
        byte[] packet = maxTrackPacket();

        for (int i = 0; i < DECODER_STRESS_ITERATIONS; i++) {
            TacticalSnapshot snapshot = decoder.decode(packet, packet.length, Collections.emptyList());

            assertEquals(TrackDatagramDecoder.MAX_TRACKS, snapshot.trackCount());
            assertEquals(LinkStatus.LIVE, snapshot.linkStatus());
            assertTrue(snapshot.alertCount() <= 1);
        }
    }

    @Test
    public void simulatedMissionAndProcessingStayWithinTrackBudget() {
        MutableClock clock = new MutableClock(1_000L);
        SimulatedTrackGenerator generator = new SimulatedTrackGenerator(clock.nowMillis());
        SituationProcessor processor = new SituationProcessor(clock);

        for (int i = 0; i < SIMULATED_MISSION_ITERATIONS; i++) {
            long now = 1_000L + i * 250L;
            clock.setNowMillis(now);

            TacticalSnapshot raw = generator.snapshotAt(now, i + 1L);
            TacticalSnapshot processed = processor.process(raw);

            assertEquals(9, raw.trackCount());
            assertEquals(9, processed.trackCount());
            assertEquals(2, processed.zones().size());
            assertTrue(processed.trackCount() <= TrackDatagramDecoder.MAX_TRACKS);
            assertTrue(processed.alertCount() <= 1);
        }
    }

    private static byte[] maxTrackPacket() {
        ByteBuffer buffer = ByteBuffer.allocate(TrackDatagramDecoder.MAX_PACKET_BYTES)
                .order(ByteOrder.BIG_ENDIAN);
        buffer.putInt(TrackDatagramDecoder.MAGIC);
        buffer.putLong(17L);
        buffer.putLong(12_345L);
        buffer.putShort((short) TrackDatagramDecoder.MAX_TRACKS);

        for (int i = 0; i < TrackDatagramDecoder.MAX_TRACKS; i++) {
            buffer.putShort((short) (i + 1));
            buffer.putDouble(43.8000 + i * 0.001);
            buffer.putDouble(-0.6000 + i * 0.001);
            buffer.putInt(10_000 + i * 100);
            buffer.putFloat((float) ((i * 11) % 360));
            buffer.putFloat(250.0f + i);
            buffer.putFloat(-100.0f + i);
            buffer.putFloat(0.85f);
        }

        return buffer.array();
    }

    private static final class MutableClock implements Clock {
        private long nowMillis;

        private MutableClock(long nowMillis) {
            this.nowMillis = nowMillis;
        }

        private void setNowMillis(long nowMillis) {
            this.nowMillis = nowMillis;
        }

        @Override
        public long nowMillis() {
            return nowMillis;
        }
    }
}
