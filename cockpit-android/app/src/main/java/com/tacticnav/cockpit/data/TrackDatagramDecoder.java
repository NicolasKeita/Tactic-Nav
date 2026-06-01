package com.tacticnav.cockpit.data;

import com.tacticnav.cockpit.domain.GeoPoint;
import com.tacticnav.cockpit.domain.LinkStatus;
import com.tacticnav.cockpit.domain.NoFlyZone;
import com.tacticnav.cockpit.domain.TacticalSnapshot;
import com.tacticnav.cockpit.domain.TacticalTrack;
import com.tacticnav.cockpit.domain.TrackStatus;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class TrackDatagramDecoder {
    public static final int MAGIC = 0x544E5331;
    public static final int HEADER_BYTES = Integer.BYTES + Long.BYTES + Long.BYTES + Short.BYTES;
    public static final int TRACK_BYTES = Short.BYTES + Double.BYTES + Double.BYTES
            + Integer.BYTES + Float.BYTES + Float.BYTES + Float.BYTES + Float.BYTES;
    public static final int MAX_TRACKS = 32;
    public static final int MAX_PACKET_BYTES = HEADER_BYTES + MAX_TRACKS * TRACK_BYTES;

    public TacticalSnapshot decode(byte[] data, int length, List<NoFlyZone> zones) throws DecodeException {
        if (data == null || zones == null) {
            throw new DecodeException("packet data and zones are required");
        }
        if (length < HEADER_BYTES || length > data.length) {
            throw new DecodeException("invalid packet length: " + length);
        }

        ByteBuffer buffer = ByteBuffer.wrap(data, 0, length).order(ByteOrder.BIG_ENDIAN);
        int magic = buffer.getInt();
        if (magic != MAGIC) {
            throw new DecodeException("invalid cockpit packet magic");
        }

        long sequenceNumber = buffer.getLong();
        long timestampMillis = buffer.getLong();
        int trackCount = Short.toUnsignedInt(buffer.getShort());
        if (sequenceNumber < 0 || timestampMillis < 0) {
            throw new DecodeException("negative sequence number or timestamp");
        }
        if (trackCount > MAX_TRACKS) {
            throw new DecodeException("too many tracks: " + trackCount);
        }

        int expectedLength = HEADER_BYTES + trackCount * TRACK_BYTES;
        if (length != expectedLength) {
            throw new DecodeException("invalid packet size: " + length + ", expected " + expectedLength);
        }

        List<TacticalTrack> tracks = new ArrayList<>(trackCount);
        for (int i = 0; i < trackCount; i++) {
            int numericId = Short.toUnsignedInt(buffer.getShort());
            GeoPoint position = new GeoPoint(buffer.getDouble(), buffer.getDouble());
            int altitudeFt = buffer.getInt();
            float headingDeg = buffer.getFloat();
            float speedKt = buffer.getFloat();
            float verticalSpeedFpm = buffer.getFloat();
            float confidence = buffer.getFloat();

            tracks.add(new TacticalTrack(
                    "track-" + numericId,
                    String.format(Locale.US, "TRK %03d", numericId),
                    position,
                    altitudeFt,
                    headingDeg,
                    speedKt,
                    verticalSpeedFpm,
                    confidence,
                    timestampMillis,
                    TrackStatus.NORMAL
            ));
        }

        return new TacticalSnapshot(timestampMillis, sequenceNumber, tracks, zones, LinkStatus.LIVE, null);
    }

    public static final class DecodeException extends Exception {
        public DecodeException(String message) {
            super(message);
        }
    }
}
