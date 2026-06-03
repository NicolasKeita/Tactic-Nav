package com.tacticnav.groundstation;

import com.tacticnav.protocol.adsb.AdsbMessage;
import com.tacticnav.protocol.adsb.AdsbPacketCodec;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

import static org.junit.jupiter.api.Assertions.*;

class GroundStationFunctionalTest {

    @Test
    void datagram_sendAndReceive_roundTrips() throws Exception {
        Class<?> gsClass = Class.forName("com.tacticnav.groundstation.GroundStation");
        Class<?> aircraftClass = Class.forName("com.tacticnav.groundstation.GroundStation$Aircraft");

        Constructor<?> ctor = aircraftClass.getDeclaredConstructor();
        ctor.setAccessible(true);
        Object ac = ctor.newInstance();

        Field f = aircraftClass.getDeclaredField("trackId"); f.setAccessible(true); f.setShort(ac, (short)22);
        f = aircraftClass.getDeclaredField("icao"); f.setAccessible(true); f.set(ac, "ICAO0001");
        f = aircraftClass.getDeclaredField("callsign"); f.setAccessible(true); f.set(ac, "FLIGHT1");
        f = aircraftClass.getDeclaredField("lat"); f.setAccessible(true); f.setDouble(ac, 44.0);
        f = aircraftClass.getDeclaredField("lon"); f.setAccessible(true); f.setDouble(ac, -0.5);
        f = aircraftClass.getDeclaredField("alt"); f.setAccessible(true); f.setDouble(ac, 54321.0);
        f = aircraftClass.getDeclaredField("heading"); f.setAccessible(true); f.setDouble(ac, 270.0);
        f = aircraftClass.getDeclaredField("speed"); f.setAccessible(true); f.setDouble(ac, 150.0);

        byte[] buffer = new byte[AdsbPacketCodec.PACKET_SIZE];
        Method serialize = gsClass.getDeclaredMethod("serializeAdsb", aircraftClass, String.class, byte[].class);
        serialize.setAccessible(true);
        serialize.invoke(null, ac, "GROUND01", buffer);

        try (DatagramSocket server = new DatagramSocket(0)) {
            int port = server.getLocalPort();
            try (DatagramSocket client = new DatagramSocket()) {
                InetAddress addr = InetAddress.getByName("127.0.0.1");
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length, addr, port);
                client.send(packet);

                byte[] recv = new byte[AdsbPacketCodec.PACKET_SIZE];
                DatagramPacket recvPacket = new DatagramPacket(recv, recv.length);
                server.setSoTimeout(2000);
                server.receive(recvPacket);

                AdsbMessage msg = AdsbPacketCodec.parse(recv, recv.length);
                assertEquals((short)22, msg.trackId());
                assertEquals("ICAO0001", msg.emitterId());
                assertEquals("FLIGHT1", msg.callsign());
                assertEquals(270.0f, msg.heading(), 1.0f);
                assertEquals(150.0f, msg.groundSpeed(), 2.0f);
            }
        }
    }
}
