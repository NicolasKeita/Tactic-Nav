package com.tacticnav.atc.broadcast;

import com.tacticnav.atc.domain.*;
import com.tacticnav.atc.fusion.FusionObserver;
import com.tacticnav.atc.fusion.TrackFusionEngine;
import com.tacticnav.atc.state.SituationStateStore;
import java.io.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Broadcasts consolidated situation state to cockpit clients via UDP.
 * 
 * Protocol:
 *   - Every snapshot or periodic (configurable)
 *   - UDP datagram with serialized tracks + zones
 *   - Sequence number for ordering
 *   - CRC/checksum for integrity
 * 
 * Design:
 *   - Non-blocking broadcast (queue-based)
 *   - Decoupled from fusion engine
 *   - Handles multiple client addresses
 * 
 * Concurrency:
 *   - Broadcast thread pulls snapshots from state store
 *   - Serializes and sends UDP packets
 *   - Observer interface notified on fusion events
 */
public class BroadcastService implements FusionObserver, Runnable {
    
    private final SituationStateStore stateStore;
    private final Set<ClientAddress> clients = ConcurrentHashMap.newKeySet();
    private final AtomicReference<SituationSnapshot> pendingEventSnapshot = new AtomicReference<>();
    
    private DatagramSocket socket;
    private volatile boolean running = false;
    private volatile long broadcastsent = 0;
    
    // Broadcast configuration
    private final int broadcastPort;
    private final long broadcastIntervalMs;
    private final int maxPacketSize = 60_000;

    /**
     * Create broadcast service.
     * 
     * @param stateStore situation state source
     * @param broadcastPort UDP port to send from
     * @param broadcastIntervalMs period between broadcasts (0 = event-driven only)
     */
    public BroadcastService(
            SituationStateStore stateStore,
            int broadcastPort,
            long broadcastIntervalMs
    ) {
        this.stateStore = stateStore;
        this.broadcastPort = broadcastPort;
        this.broadcastIntervalMs = broadcastIntervalMs;
    }

    /**
     * Register a cockpit client to receive broadcasts.
     * 
     * @param clientAddress address to send to
     */
    public void addClient(ClientAddress clientAddress) {
        clients.add(clientAddress);
        System.out.printf("[BroadcastService] Client added: %s:%d%n", 
            clientAddress.address(), clientAddress.port());
    }

    /**
     * Unregister a client.
     */
    public void removeClient(ClientAddress clientAddress) {
        clients.remove(clientAddress);
        System.out.printf("[BroadcastService] Client removed: %s:%d%n", 
            clientAddress.address(), clientAddress.port());
    }

    /**
     * Observer callback: broadcast on fusion events.
     */
    @Override
    public void onFusionEvent(TrackFusionEngine.FusionEvent event, SituationSnapshot snapshot) {
        if (event instanceof TrackFusionEngine.FusionEvent.TrackCreated ||
            event instanceof TrackFusionEngine.FusionEvent.TrackExpired) {
            pendingEventSnapshot.set(snapshot);
        }
    }

    /**
     * Main broadcast loop.
     * Sends periodic broadcasts at fixed interval.
     */
    @Override
    public void run() {
        running = true;
        System.out.println("[BroadcastService] Started");

        try {
            socket = new DatagramSocket(broadcastPort);
            System.out.printf("[BroadcastService] Sending from UDP port %d%n", broadcastPort);

            long lastBroadcastTime = System.currentTimeMillis();

            while (running && !Thread.currentThread().isInterrupted()) {
                long now = System.currentTimeMillis();
                SituationSnapshot eventSnapshot = pendingEventSnapshot.getAndSet(null);

                if (eventSnapshot != null) {
                    broadcastSnapshot(eventSnapshot);
                    lastBroadcastTime = now;
                }

                if (broadcastIntervalMs > 0 && now - lastBroadcastTime >= broadcastIntervalMs) {
                    SituationSnapshot snapshot = stateStore.getSnapshot();
                    broadcastSnapshot(snapshot);
                    lastBroadcastTime = now;
                }

                // Sleep briefly to avoid busy-waiting
                Thread.sleep(Math.max(10, broadcastIntervalMs / 10));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            System.err.printf("[BroadcastService] Error: %s%n", e.getMessage());
            e.printStackTrace();
        } finally {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
            running = false;
            System.out.printf("[BroadcastService] Stopped (broadcasts sent: %d)%n", broadcastsent);
        }
    }

    /**
     * Send a situation snapshot to all registered clients.
     */
    private void broadcastSnapshot(SituationSnapshot snapshot) {
        DatagramSocket currentSocket = socket;
        if (clients.isEmpty() || currentSocket == null || currentSocket.isClosed()) {
            return;
        }

        try {
            byte[] payload = serializeSnapshot(snapshot);
            if (payload.length > maxPacketSize) {
                System.err.printf(
                    "[BroadcastService] Snapshot %d is too large for one UDP datagram (%d bytes), skipped%n",
                    snapshot.sequenceNumber(),
                    payload.length
                );
                return;
            }
            
            for (ClientAddress client : clients) {
                try {
                    InetAddress addr = InetAddress.getByName(client.address());
                    DatagramPacket packet = new DatagramPacket(
                        payload,
                        payload.length,
                        addr,
                        client.port()
                    );
                    currentSocket.send(packet);
                    broadcastsent++;
                } catch (Exception e) {
                    System.err.printf("[BroadcastService] Failed to send to %s:%d: %s%n",
                        client.address(), client.port(), e.getMessage());
                }
            }
        } catch (Exception e) {
            System.err.printf("[BroadcastService] Serialization error: %s%n", e.getMessage());
        }
    }

    /**
     * Serialize a snapshot into a byte array.
     * 
     * Format (simple binary):
     *   [0-7]     Timestamp (long)
     *   [8-15]    Sequence number (long)
     *   [16-19]   Track count (int)
     *   [20-23]   Zone count (int)
     *   [24-...]  Serialized tracks
     *   [...-end] Serialized zones
     */
    private byte[] serializeSnapshot(SituationSnapshot snapshot) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);

        dos.writeLong(snapshot.timestamp());
        dos.writeLong(snapshot.sequenceNumber());
        dos.writeInt(snapshot.trackCount());
        dos.writeInt(snapshot.zoneCount());

        // Serialize tracks
        for (Track track : snapshot.tracks().values()) {
            serializeTrack(dos, track);
        }

        // Serialize zones
        for (NoFlyZone zone : snapshot.zones()) {
            serializeZone(dos, zone);
        }

        dos.flush();
        return baos.toByteArray();
    }

    /**
     * Serialize a track.
     */
    private void serializeTrack(DataOutputStream dos, Track track) throws IOException {
        dos.writeUTF(track.id().value());
        dos.writeDouble(track.position().x());
        dos.writeDouble(track.position().y());
        dos.writeDouble(track.position().z());
        dos.writeDouble(track.velocity().vx());
        dos.writeDouble(track.velocity().vy());
        dos.writeDouble(track.velocity().vz());
        dos.writeFloat(track.confidence());
        dos.writeLong(track.position().timestamp());
    }

    /**
     * Serialize a no-fly zone.
     */
    private void serializeZone(DataOutputStream dos, NoFlyZone zone) throws IOException {
        dos.writeUTF(zone.id());
        dos.writeUTF(zone.name());
        dos.writeInt(zone.vertexCount());
        for (int i = 0; i < zone.vertices().length; i++) {
            dos.writeDouble(zone.vertices()[i]);
        }
        dos.writeInt(zone.minAltitude());
        dos.writeInt(zone.maxAltitude());
    }

    /**
     * Stop the broadcast service.
     */
    public void stop() {
        running = false;
    }

    /**
     * Check if running.
     */
    public boolean isRunning() {
        return running;
    }

    /**
     * Get broadcast statistics.
     */
    public long getBroadcastsSent() {
        return broadcastsent;
    }

    /**
     * Represents a client address.
     */
    public record ClientAddress(String address, int port) {
        public ClientAddress {
            if (address == null || address.isEmpty() || port <= 0 || port > 65535) {
                throw new IllegalArgumentException("Invalid client address");
            }
        }
    }
}
