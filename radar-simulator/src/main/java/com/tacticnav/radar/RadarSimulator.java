package com.tacticnav.radar;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.ByteBuffer;
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

    public void start() {
        System.out.printf("Starting Radar-%d -> %s:%d (100ms cadence, UDP binary)\n", radarId, host, port);
        byte[] buffer = new byte[32]; // fixed packet size

        try (DatagramSocket socket = new DatagramSocket()) {
            InetAddress addr = InetAddress.getByName(host);

            // initial track state
            float lat = 48.8566f + (rng.nextFloat() - 0.5f) * 0.1f; // around Paris
            float lon = 2.3522f + (rng.nextFloat() - 0.5f) * 0.1f;
            float alt = 1000.0f + rng.nextFloat() * 200.0f;
            float speed = 150.0f + rng.nextFloat() * 50.0f;

            while (!Thread.currentThread().isInterrupted()) {
                long timestamp = System.currentTimeMillis();
                Track track = new Track((short) radarId, lat, lon, alt, speed, timestamp);

                PacketSerializer.serializeInto(track, buffer);

                DatagramPacket pkt = new DatagramPacket(buffer, buffer.length, addr, port);
                socket.send(pkt);

                // small deterministic motion for next packet
                lat += (rng.nextFloat() - 0.5f) * 0.0005f;
                lon += (rng.nextFloat() - 0.5f) * 0.0005f;
                alt += (rng.nextFloat() - 0.5f) * 0.5f;

                Thread.sleep(100);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            System.err.println("Radar simulator error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
