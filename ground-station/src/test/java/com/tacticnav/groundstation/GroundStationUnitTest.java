package com.tacticnav.groundstation;

import com.tacticnav.protocol.adsb.AdsbMessage;
import com.tacticnav.protocol.adsb.AdsbPacketCodec;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

class GroundStationUnitTest {

    @Test
    void serializeAdsb_producesParsableAdsbMessage() throws Exception {
        Class<?> gsClass = Class.forName("com.tacticnav.groundstation.GroundStation");
        Class<?> aircraftClass = Class.forName("com.tacticnav.groundstation.GroundStation$Aircraft");

        Constructor<?> ctor = aircraftClass.getDeclaredConstructor();
        ctor.setAccessible(true);
        Object ac = ctor.newInstance();

        Field f = aircraftClass.getDeclaredField("trackId"); f.setAccessible(true); f.setShort(ac, (short)123);
        f = aircraftClass.getDeclaredField("icao"); f.setAccessible(true); f.set(ac, "ABC12345");
        f = aircraftClass.getDeclaredField("callsign"); f.setAccessible(true); f.set(ac, "TEST123");
        f = aircraftClass.getDeclaredField("lat"); f.setAccessible(true); f.setDouble(ac, 44.0);
        f = aircraftClass.getDeclaredField("lon"); f.setAccessible(true); f.setDouble(ac, -0.5);
        f = aircraftClass.getDeclaredField("alt"); f.setAccessible(true); f.setDouble(ac, 1000.0);
        f = aircraftClass.getDeclaredField("heading"); f.setAccessible(true); f.setDouble(ac, 90.0);
        f = aircraftClass.getDeclaredField("speed"); f.setAccessible(true); f.setDouble(ac, 250.0);

        byte[] buffer = new byte[AdsbPacketCodec.PACKET_SIZE];
        Method serialize = gsClass.getDeclaredMethod("serializeAdsb", aircraftClass, String.class, byte[].class);
        serialize.setAccessible(true);
        serialize.invoke(null, ac, "GS-UT", buffer);

        AdsbMessage msg = AdsbPacketCodec.parse(buffer, buffer.length);
        assertEquals((short)123, msg.trackId());
        assertEquals("ABC12345", msg.emitterId());
        assertEquals("TEST123", msg.callsign());
        assertEquals(90.0f, msg.heading(), 1.0f);
        assertEquals(250.0f, msg.groundSpeed(), 2.0f);
        assertEquals("GS-UT", msg.stationId());
    }
}
