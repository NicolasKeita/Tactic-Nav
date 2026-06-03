package com.tacticnav.groundstation;

import com.tacticnav.protocol.adsb.AdsbMessage;
import com.tacticnav.protocol.adsb.AdsbPacketCodec;

import java.io.IOException;
import java.io.InputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Simple ground station simulator that emits custom ADS-B UDP packets and relays them to the ATC.
 * The packets are 112 bytes long and follow a home-grown simplified ADS-B payload layout.
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
            double metersPerDegree = 111320.0; // approximate conversion
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

        String forwardHost = getArgOrProp(args, "--host", "forward.host", cfg, "127.0.0.1");
        int forwardPort = Integer.parseInt(getArgOrProp(args, "--port", "forward.port", cfg, "15001"));
        int count = Integer.parseInt(getArgOrProp(args, "--count", "simulator.count", cfg, "8"));
        long intervalMs = Long.parseLong(getArgOrProp(args, "--interval", "simulator.interval.ms", cfg, "500"));
        String stationId = getArgOrProp(args, "--id", "station.id", cfg, "GS-001");

        System.out.printf("GroundStation %s -> %s:%d | sim.count=%d interval=%dms%n", stationId, forwardHost, forwardPort, count, intervalMs);

        List<Aircraft> acList = makeAircraft(count);
        DatagramSocket sock = new DatagramSocket();
        InetAddress addr = InetAddress.getByName(forwardHost);
        byte[] buffer = new byte[AdsbPacketCodec.PACKET_SIZE];

        ScheduledExecutorService exec = Executors.newSingleThreadScheduledExecutor();
        final double dtSeconds = intervalMs / 1000.0;
        final Random rnd = new Random();

        exec.scheduleAtFixedRate(() -> {
            try {
                for (Aircraft ac : acList) {
                    ac.step(dtSeconds);
                    serializeAdsb(ac, stationId, buffer);
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length, addr, forwardPort);
                    sock.send(packet);
                }
                if (rnd.nextDouble() < 0.3) {
                    Aircraft a = acList.get(rnd.nextInt(acList.size()));
                    a.heading = (a.heading + (rnd.nextDouble() * 40.0 - 20.0) + 360.0) % 360.0;
                    a.speed = Math.max(80.0, Math.min(320.0, a.speed + rnd.nextDouble() * 20.0 - 10.0));
                }
            } catch (IOException e) {
                System.err.println("Send error: " + e.getMessage());
            }
        }, 0, intervalMs, TimeUnit.MILLISECONDS);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            exec.shutdownNow();
            sock.close();
            System.out.println("GroundStation stopped.");
        }));
    }

    private static void serializeAdsb(Aircraft aircraft, String stationId, byte[] buffer) {
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

    private static List<Aircraft> makeAircraft(int count) {
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
