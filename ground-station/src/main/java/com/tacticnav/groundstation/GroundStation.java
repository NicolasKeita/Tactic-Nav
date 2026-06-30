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
    private static final double METERS_PER_DEGREE = 111320.0;

    /**
     * Internal class representing an aircraft for simulation purposes.
     * Used by test helper methods (serializeAdsb, makeAircraft).
     */
    static class Aircraft {
        short trackId;
        String icao;
        String callsign;
        double lat;
        double lon;
        double alt;
        double heading; // degrees (0-360)
        double speed;   // meters per second

        /**
         * Update aircraft position based on its velocity and time step.
         * Uses spherical Earth approximation for coordinate calculations.
         * 
         * @param dtSeconds time step in seconds
         */
        void step(double dtSeconds) {
            double distance = speed * dtSeconds;
            // Protect against division by zero near poles
            double cosLat = Math.max(0.0001, Math.cos(Math.toRadians(lat)));
            
            double dLat = distance * Math.cos(Math.toRadians(heading)) / METERS_PER_DEGREE;
            double dLon = distance * Math.sin(Math.toRadians(heading)) / (METERS_PER_DEGREE * cosLat);
            
            lat += dLat;
            lon += dLon;
        }
    }

    /**
     * Main entry point for the GroundStation application.
     * <p>
     * Command-line arguments (override properties file):
     *   --listen-port PORT   - UDP port to listen on (default: 15000)
     *   --host HOST          - ATC server host to forward packets to (default: 127.0.0.1)
     *   --port PORT          - ATC server port (default: 15001)
     *   --id ID              - Station identifier (default: GS-001)
     * <p>
     * Properties file: ground-station.properties in classpath
     * 
     * @param args command-line arguments
     * @throws Exception if configuration cannot be loaded or parsed
     */
    public static void main(String[] args) throws Exception {
        // Load configuration from properties file
        Properties cfg = new Properties();
        try (InputStream in = GroundStation.class.getResourceAsStream("/ground-station.properties")) {
            if (in != null) {
                cfg.load(in);
            }
        }

        // Parse configuration with command-line overrides
        int listenPort;
        try {
            listenPort = Integer.parseInt(getArgOrProp(args, "--listen-port", "listen.port", cfg, "15000"));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid listen-port value: " + 
                getArgOrProp(args, "--listen-port", "listen.port", cfg, "15000"));
        }
        
        String forwardHost = getArgOrProp(args, "--host", "forward.host", cfg, "127.0.0.1");
        
        int forwardPort;
        try {
            forwardPort = Integer.parseInt(getArgOrProp(args, "--port", "forward.port", cfg, "15001"));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid port value: " + 
                getArgOrProp(args, "--port", "forward.port", cfg, "15001"));
        }
        
        String stationId = getArgOrProp(args, "--id", "station.id", cfg, "GS-001");

        // Display startup information
        String localIp = findLocalIp();
        System.out.printf("GroundStation %s listening on %s:%d -> %s:%d%n",
                stationId, localIp, listenPort, forwardHost, forwardPort);
        System.out.println("Press Ctrl+C to stop");

        // Main relay loop
        try (DatagramSocket listenSock = new DatagramSocket(listenPort)) {
            InetAddress forwardAddr = InetAddress.getByName(forwardHost);
            byte[] buffer = new byte[AdsbPacketCodec.PACKET_SIZE];
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            DateTimeFormatter timestampFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

            while (true) {
                // Receive packet
                listenSock.receive(packet);

                // Log reception
                String timestamp = LocalDateTime.now().format(timestampFormatter);
                System.out.printf("[%s] %s Received %d bytes from %s:%d%n",
                        stationId, 
                        timestamp, 
                        packet.getLength(),
                        packet.getAddress().getHostAddress(),
                        packet.getPort());

                // Forward packet to ATC server
                DatagramPacket forwardPacket = new DatagramPacket(
                        packet.getData(), packet.getLength(), forwardAddr, forwardPort);
                listenSock.send(forwardPacket);
            }
        } catch (IOException e) {
            System.err.printf("GroundStation error: %s%n", e.getMessage());
            if (e.getCause() != null) {
                System.err.printf("Cause: %s%n", e.getCause().getMessage());
            }
        }
    }

    /**
     * Find the first non-loopback IPv4 address of this machine.
     * Used to display the local IP address on startup.
     * 
     * @return the first non-loopback IPv4 address, or "127.0.0.1" if none found
     */
    private static String findLocalIp() {
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                // Skip loopback and down interfaces
                if (ni.isLoopback() || !ni.isUp()) {
                    continue;
                }
                
                // Look for first IPv4 address
                for (InetAddress addr : Collections.list(ni.getInetAddresses())) {
                    if (addr instanceof Inet4Address) {
                        return addr.getHostAddress();
                    }
                }
            }
        } catch (SocketException e) {
            // Fallback to loopback if network interfaces cannot be enumerated
            // This can happen in restricted environments or without network permissions
        }
        return "127.0.0.1";
    }

    // -- Methods below are used by unit / functional tests via reflection --

    /**
     * Serialize an Aircraft object into an ADS-B packet buffer.
     * Converts aircraft position (lat/lon/alt) to spherical coordinates (azimuth/elevation/range).
     * 
     * @param aircraft the aircraft to serialize
     * @param stationId the station identifier
     * @param buffer the output buffer (must be at least AdsbPacketCodec.PACKET_SIZE bytes)
     */
    static void serializeAdsb(Aircraft aircraft, String stationId, byte[] buffer) {
        // Calculate relative position from base station
        double avgLat = (aircraft.lat + BASE_LAT) / 2.0;
        double cosAvgLat = Math.cos(Math.toRadians(avgLat));
        
        double dx = (aircraft.lon - BASE_LON) * cosAvgLat * METERS_PER_DEGREE;
        double dy = (aircraft.lat - BASE_LAT) * METERS_PER_DEGREE;
        
        double horizontalDistance = Math.hypot(dx, dy);
        
        // Calculate azimuth: 0° = North, 90° = East
        // atan2(dx, dy) gives angle from north axis
        float azimuth = (float) ((Math.toDegrees(Math.atan2(dx, dy)) + 360.0) % 360.0);
        
        // Calculate elevation: angle from horizontal plane
        float elevation = (float) Math.toDegrees(Math.atan2(aircraft.alt, horizontalDistance));
        
        // Calculate slant range: direct distance from radar
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

    /**
     * Get a configuration value from command-line arguments or properties.
     * Command-line arguments take precedence over properties.
     * 
     * @param args command-line arguments
     * @param argName the argument name (e.g., "--listen-port")
     * @param propName the property name (e.g., "listen.port")
     * @param props the properties object
     * @param def default value if not found in args or props
     * @return the value from args, props, or default
     */
    private static String getArgOrProp(String[] args, String argName, String propName, Properties props, String def) {
        if (args != null) {
            for (int i = 0; i < args.length - 1; i++) {
                if (argName.equals(args[i])) {
                    return args[i + 1];
                }
            }
        }
        return props != null ? props.getProperty(propName, def) : def;
    }

    /**
     * Create a list of random aircraft for testing purposes.
     * Each aircraft has random position, altitude, heading, and speed.
     * 
     * @param count the number of aircraft to create
     * @return list of randomly generated aircraft
     */
    static List<Aircraft> makeAircraft(int count) {
        if (count < 0) {
            throw new IllegalArgumentException("count must be non-negative");
        }
        
        List<Aircraft> list = new ArrayList<>(count);
        Random rnd = new Random(42); // Fixed seed for reproducibility
        
        for (int i = 0; i < count; i++) {
            Aircraft a = new Aircraft();
            a.trackId = (short) (i + 1);
            a.icao = String.format("%08X", rnd.nextInt());
            a.callsign = "TST" + (100 + i);
            // Random position within +/- 0.5 degrees of base
            a.lat = BASE_LAT + (rnd.nextDouble() - 0.5) * 1.0;
            a.lon = BASE_LON + (rnd.nextDouble() - 0.5) * 1.0;
            // Random altitude between 1000m and 11000m
            a.alt = 1000 + rnd.nextDouble() * 10000;
            // Random heading between 0° and 360°
            a.heading = rnd.nextDouble() * 360.0;
            // Random speed between 100 m/s and 300 m/s
            a.speed = 100 + rnd.nextDouble() * 200;
            list.add(a);
        }
        return list;
    }
}