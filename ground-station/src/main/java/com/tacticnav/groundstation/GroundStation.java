package com.tacticnav.groundstation;

import com.tacticnav.protocol.adsb.AdsbMessage;
import com.tacticnav.protocol.adsb.AdsbPacketCodec;

import java.io.IOException;
import java.io.InputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Inet4Address;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.Random;

/**
 * Simple ground station that listens for incoming ADS-B UDP packets and relays
 * them to the ATC server. Displays its own IP address and listening port on startup.
 */
public class GroundStation {

    private static final double BASE_LAT = 44.0;
    private static final double BASE_LON = -0.5;

    static class Aircraft {
        short trackId;
        String icao;
        String callsign;
        double lat;
        double lon;
        double alt;
        double heading;
        double speed; // m/s

        void step(double dtSeconds) {
            double metersPerDegree = 111320.0;
            double distance = speed * dtSeconds;
            double dLat = distance * Math.cos(Math.toRadians(heading)) / metersPerDegree;
            double dLon = distance * Math.sin(Math.toRadians(heading)) / (metersPerDegree * Math.max(0.0001, Math.cos(Math.toRadians(lat))));
            lat += dLat;
            lon += dLon;
        }
    }

    public static void main(String[] args) throws Exception {
        Properties cfg = new Properties();
        try (InputStream in = GroundStation.class.getResourceAsStream("/ground-station.properties")) {
            if (in != null) cfg.load(in);
        }

        int listenPort = Integer.parseInt(getArgOrProp(args, "--listen-port", "listen.port", cfg, "15000"));
        String forwardHost = getArgOrProp(args, "--host", "forward.host", cfg, "127.0.0.1");
        int forwardPort = Integer.parseInt(getArgOrProp(args, "--port", "forward.port", cfg, "15001"));
        String stationId = getArgOrProp(args, "--id", "station.id", cfg, "GS-001");

        String localIp = findLocalIp();
        System.out.printf("GroundStation %s listening on %s:%d -> %s:%d%n",
                stationId, localIp, listenPort, forwardHost, forwardPort);

        try (DatagramSocket listenSock = new DatagramSocket(listenPort)) {
            InetAddress forwardAddr = InetAddress.getByName(forwardHost);
            byte[] buffer = new byte[AdsbPacketCodec.PACKET_SIZE];

            while (true) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                listenSock.receive(packet);

                String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                System.out.printf("[%s] %s Received %d bytes%n",
                        stationId, timestamp, packet.getLength());

                DatagramPacket forwardPacket = new DatagramPacket(
                        packet.getData(), packet.getLength(), forwardAddr, forwardPort);
                listenSock.send(forwardPacket);
            }
        } catch (IOException e) {
            System.err.println("GroundStation error: " + e.getMessage());
        }
    }

    private static String findLocalIp() {
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (ni.isLoopback() || !ni.isUp()) continue;
                for (InetAddress addr : Collections.list(ni.getInetAddresses())) {
                    if (addr instanceof Inet4Address) {
                        return addr.getHostAddress();
                    }
                }
            }
        } catch (SocketException e) {
            // fallback
        }
        return "127.0.0.1";
    }

    // -- Methods below are used by unit / functional tests via reflection --

    static void serializeAdsb(Aircraft aircraft, String stationId, byte[] buffer) {
        double dx = (aircraft.lon - BASE_LON) * Math.cos(Math.toRadians((aircraft.lat + BASE_LAT) / 2.0)) * 111320.0;
        double dy = (aircraft.lat - BASE_LAT) * 111320.0;
        double horizontalDistance = Math.hypot(dx, dy);
        float azimuth = (float) ((Math.toDegrees(Math.atan2(dx, dy)) + 360.0) % 360.0);
        float elevation = (float) Math.toDegrees(Math.atan2(aircraft.alt, horizontalDistance));
        float slantRange = (float) Math.hypot(horizontalDistance, aircraft.alt);

        AdsbMessage message = new AdsbMessage(
            aircraft.trackId,
            aircraft.icao,
            aircraft.callsign,
            azimuth,
            elevation,
            slantRange,
            (float) aircraft.heading,
            (float) aircraft.speed,
            Instant.now().toEpochMilli(),
            stationId
        );

        AdsbPacketCodec.serializeInto(message, buffer);
    }

    private static String getArgOrProp(String[] args, String argName, String propName, Properties props, String def) {
        for (int i = 0; i < args.length - 1; i++) {
            if (args[i].equals(argName)) return args[i + 1];
        }
        return props.getProperty(propName, def);
    }

    static List<Aircraft> makeAircraft(int count) {
        List<Aircraft> list = new ArrayList<>();
        Random rnd = new Random(42);
        for (int i = 0; i < count; i++) {
            Aircraft a = new Aircraft();
            a.trackId = (short) (i + 1);
            a.icao = String.format("%08X", rnd.nextInt());
            a.callsign = "TST" + (100 + i);
            a.lat = BASE_LAT + (rnd.nextDouble() - 0.5) * 1.0;
            a.lon = BASE_LON + (rnd.nextDouble() - 0.5) * 1.0;
            a.alt = 1000 + rnd.nextDouble() * 10000;
            a.heading = rnd.nextDouble() * 360.0;
            a.speed = 100 + rnd.nextDouble() * 200;
            list.add(a);
        }
        return list;
    }
}