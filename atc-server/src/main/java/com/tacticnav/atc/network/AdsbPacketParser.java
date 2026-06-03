package com.tacticnav.atc.network;

import com.tacticnav.atc.domain.RadarInputMessage;
import com.tacticnav.protocol.ProtocolException;
import com.tacticnav.protocol.adsb.AdsbMessage;
import com.tacticnav.protocol.adsb.AdsbPacketCodec;

/**
 * Parses raw UDP ADS-B packets into normalized RadarInputMessage records.
 * This allows the ATC to keep the existing fusion pipeline while adopting
 * a new ADS-B packet transport format.
 */
public final class AdsbPacketParser {

    public RadarInputMessage parse(byte[] packetData, int length) throws ParseException {
        AdsbMessage packet;
        try {
            packet = AdsbPacketCodec.parse(packetData, length);
        } catch (ProtocolException e) {
            throw new ParseException(e.getMessage());
        }

        if (!Float.isFinite(packet.azimuth()) || packet.azimuth() < 0f || packet.azimuth() > 360f) {
            throw new ParseException("Invalid azimuth: " + packet.azimuth());
        }
        if (!Float.isFinite(packet.elevation()) || packet.elevation() < -90f || packet.elevation() > 90f) {
            throw new ParseException("Invalid elevation: " + packet.elevation());
        }
        if (!Float.isFinite(packet.slantRange()) || packet.slantRange() < 0f) {
            throw new ParseException("Invalid slantRange: " + packet.slantRange());
        }
        if (packet.timestamp() < 0) {
            throw new ParseException("Invalid timestamp: " + packet.timestamp());
        }

        return new RadarInputMessage(
            packet.trackId(),
            packet.azimuth(),
            packet.elevation(),
            packet.slantRange(),
            packet.timestamp()
        );
    }

    public static final class ParseException extends Exception {
        public ParseException(String message) {
            super(message);
        }
    }
}
