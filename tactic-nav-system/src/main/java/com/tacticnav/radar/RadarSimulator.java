package com.tacticnav.radar;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Random;

public class RadarSimulator {
    private static final Logger log = LoggerFactory.getLogger(RadarSimulator.class);
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
        log.info("Starting Radar-{} -> {}:{} (800ms cadence, UDP binary)", radarId, host, port);
        byte[] buffer = new byte[28];

        try (DatagramSocket socket = new DatagramSocket()) {
            InetAddress addr = InetAddress.getByName(host);
            String localIp = InetAddress.getLocalHost().getHostAddress();

            float azimuth = rng.nextFloat() * 360f;
            float elevation = (rng.nextFloat() - 0.5f) * 60f;
            float slantRange = 5000f + rng.nextFloat() * 45000f;

            while (!Thread.currentThread().isInterrupted()) {
                long timestamp = System.currentTimeMillis();
                Track track = new Track((short) radarId, azimuth, elevation, slantRange, timestamp);

                PacketSerializer.serializeInto(track, buffer);

                DatagramPacket pkt = new DatagramPacket(buffer, buffer.length, addr, port);
                socket.send(pkt);
                log.info("[Radar-{}] UDP {}:{} -> {}:{} (azimuth={}°, elevation={}°, range={}m)",
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
            log.error("Radar simulator error", e);
        }
    }
}
