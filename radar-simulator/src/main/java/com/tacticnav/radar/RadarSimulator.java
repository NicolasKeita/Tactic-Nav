package com.tacticnav.radar;

import com.tacticnav.protocol.RadarObservation;
import com.tacticnav.protocol.RadarPacketCodec;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Random;

public class RadarSimulator {
    private final int radarId;
    private final String host;
    private final int port;
    private final Random rng = new Random();

    public RadarSimulator(int radarId, String host, int port) {
        this.radarId = radarId;
        this.host = host;
        this.port = port;
    }

    /**
     * Starts the radar simulator loop. Generates spherical coordinates:
     * azimuth (0-360°), elevation (-30° to +30°), slant range (5-50 km).
     * Each field is updated slightly every iteration to simulate a rotating radar sweep.
     */
    public void start() {
        System.out.printf("Starting Radar-%d -> %s:%d (800ms cadence, UDP binary)%n", radarId, host, port);
        byte[] buffer = new byte[RadarPacketCodec.PACKET_SIZE];

        try (DatagramSocket socket = new DatagramSocket()) {
            InetAddress addr = InetAddress.getByName(host);
            String localIp = InetAddress.getLocalHost().getHostAddress();

            float azimuth = rng.nextFloat() * 360f;
            float elevation = (rng.nextFloat() - 0.5f) * 60f;
            float slantRange = 5000f + rng.nextFloat() * 45000f;

            while (!Thread.currentThread().isInterrupted()) {
                long timestamp = System.currentTimeMillis();
                RadarObservation observation = new RadarObservation((short) radarId, azimuth, elevation, slantRange, timestamp);

                RadarPacketCodec.serializeInto(observation, buffer);

                DatagramPacket pkt = new DatagramPacket(buffer, buffer.length, addr, port);
                socket.send(pkt);
                String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"));
                System.out.printf("[%s] [Radar-%d] UDP %s:%d -> %s:%d (azimuth=%.1f°, elevation=%.1f°, range=%.0fm)%n",
                        ts,
                        radarId,
                        localIp,
                        socket.getLocalPort(),
                        host, port,
                        azimuth, elevation, slantRange);

                azimuth = (azimuth + 1.5f + (rng.nextFloat() - 0.5f) * 0.5f) % 360f;
                elevation += (rng.nextFloat() - 0.5f) * 1f;
                elevation = Math.max(-30f, Math.min(30f, elevation));
                slantRange += (rng.nextFloat() - 0.5f) * 50f;

                Thread.sleep(800);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            System.err.println("Radar simulator error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
