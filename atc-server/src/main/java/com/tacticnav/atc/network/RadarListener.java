package com.tacticnav.atc.network;

import com.tacticnav.atc.domain.RadarInputMessage;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketTimeoutException;
import java.util.function.Consumer;

/**
 * Listens on a UDP port for radar packets from a single source.
 * 
 * Design:
 *   - One listener per radar source
 *   - Runs in a dedicated thread
 *   - Parses packets and forwards valid messages to a handler (callback)
 *   - Silently discards invalid packets (logs only)
 *   - No business logic; only receives, parses, forwards
 * 
 * Concurrency:
 *   - Thread-safe: runs in dedicated thread, no shared mutable state
 *   - Handler should be thread-safe or enqueue work to fusion engine
 */
public class RadarListener implements Runnable {
    private final int radarId;
    private final int port;
    private final RadarPacketParser parser;
    private final Consumer<RadarInputMessage> handler;
    private final byte[] buffer = new byte[28];
    
    private DatagramSocket socket;
    private volatile boolean running = false;
    private volatile Throwable lastError = null;

    /**
     * Create a listener for a single radar source.
     * 
     * @param radarId ID of this radar (for tracking)
     * @param port UDP port to listen on
     * @param handler callback for successfully parsed messages
     */
    public RadarListener(int radarId, int port, Consumer<RadarInputMessage> handler) {
        this.radarId = radarId;
        this.port = port;
        this.handler = handler;
        this.parser = new RadarPacketParser(radarId);
    }

    /**
     * Start listening for packets.
     * Runs until interrupted or socket error.
     */
    @Override
    public void run() {
        running = true;
        try (DatagramSocket datagramSocket = new DatagramSocket(port)) {
            socket = datagramSocket;
            datagramSocket.setSoTimeout(500);
            System.out.printf("[RadarListener-%d] Listening on UDP port %d%n", radarId, port);
            
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            
            while (running && !Thread.currentThread().isInterrupted()) {
                packet.setLength(buffer.length);

                try {
                    datagramSocket.receive(packet);
                } catch (SocketTimeoutException ignored) {
                    continue;
                }
                
                try {
                    RadarInputMessage message = parser.parse(buffer, packet.getLength());
                    handler.accept(message);
                } catch (RadarPacketParser.ParseException e) {
                    System.err.printf("[RadarListener-%d] Parse error: %s%n", radarId, e.getMessage());
                }
            }
        } catch (Exception e) {
            if (running) {
                lastError = e;
                System.err.printf("[RadarListener-%d] Fatal error: %s%n", radarId, e.getMessage());
                e.printStackTrace();
            }
        } finally {
            socket = null;
            running = false;
            System.out.printf("[RadarListener-%d] Stopped%n", radarId);
        }
    }

    /**
     * Stop listening gracefully.
     */
    public void stop() {
        running = false;
        DatagramSocket currentSocket = socket;
        if (currentSocket != null) {
            currentSocket.close();
        }
    }

    /**
     * Check if listener is currently running.
     */
    public boolean isRunning() {
        return running;
    }

    /**
     * Get the last error that occurred (if any).
     */
    public Throwable getLastError() {
        return lastError;
    }
}
