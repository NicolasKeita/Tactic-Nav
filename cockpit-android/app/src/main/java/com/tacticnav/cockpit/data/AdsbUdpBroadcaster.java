package com.tacticnav.cockpit.data;

import com.tacticnav.cockpit.domain.GeoPoint;
import com.tacticnav.cockpit.domain.TacticalSnapshot;
import com.tacticnav.cockpit.domain.TacticalTrack;
import com.tacticnav.cockpit.geo.GeoMath;
import com.tacticnav.cockpit.domain.NoFlyZone;

import java.io.IOException;
import java.util.List;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Generates simulated tactical tracks and broadcasts them as ADS-B packets
 * via UDP to a remote ground station receiver.
 *
 * Each track is encoded as a 112-byte ADS-B datagram containing bearing,
 * elevation, slant range, heading, ground speed, and identification data.
 */
public final class AdsbUdpBroadcaster implements AtcTrackSource {
    private static final long PERIOD_MILLIS = 250L;
    private static final String STATION_ID = "COCKPIT";
    private static final String EMITTER_ID = "TACNAV";

    /** Ground station reference point (Toulouse area, same as default viewport). */
    private static final GeoPoint GROUND_STATION = new GeoPoint(
            CockpitConstants.VIEWPORT_CENTER_LAT,
            CockpitConstants.VIEWPORT_CENTER_LON
    );
    private static final int GROUND_ALTITUDE_FT = 200;

    private final String host;
    private final int port;
    private final SimulatedTrackGenerator generator;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicInteger trackSequence = new AtomicInteger(1);

    private ScheduledExecutorService executor;

    public AdsbUdpBroadcaster(String host, int port) {
        if (host == null || host.isEmpty()) {
            throw new IllegalArgumentException("host cannot be null or empty");
        }
        if (port <= 0 || port > 65_535) {
            throw new IllegalArgumentException("port must be a valid UDP port");
        }
        this.host = host;
        this.port = port;
        this.generator = new SimulatedTrackGenerator(System.currentTimeMillis());
    }

    @Override
    public void start(Listener listener) {
        if (listener == null) {
            throw new IllegalArgumentException("listener cannot be null");
        }
        if (!running.compareAndSet(false, true)) {
            return;
        }

        executor = Executors.newSingleThreadScheduledExecutor(
                new NamedThreadFactory("cockpit-adsb-broadcaster")
        );
        executor.scheduleAtFixedRate(
                () -> broadcastTracks(listener),
                0L,
                PERIOD_MILLIS,
                TimeUnit.MILLISECONDS
        );
    }

    @Override
    public void stop() {
        running.set(false);
        ScheduledExecutorService current = executor;
        executor = null;
        if (current != null) {
            current.shutdownNow();
        }
    }

    private void broadcastTracks(Listener listener) {
        if (!running.get()) {
            return;
        }

        try {
            long nowMillis = System.currentTimeMillis();
            long sequenceNumber = trackSequence.getAndIncrement();
            TacticalSnapshot snapshot = generator.snapshotAt(nowMillis, sequenceNumber);

            // Toujours délivrer le snapshot localement pour l'affichage cockpit
            listener.onSnapshot(snapshot);

            // Envoyer les paquets ADS-B par UDP (en cas d'échec, on logge mais on continue)
            try {
                sendAdsbPackets(snapshot, nowMillis);
            } catch (Exception ignored) {
                // L'affichage local continue même si l'UDP échoue
            }
        } catch (RuntimeException error) {
            listener.onSourceError(error);
        }
    }

    private void sendAdsbPackets(TacticalSnapshot snapshot, long nowMillis) throws IOException {
        try (DatagramSocket socket = new DatagramSocket()) {
            InetSocketAddress target = new InetSocketAddress(host, port);
            byte[] packetBuffer = new byte[AdsbPacketCodec.PACKET_SIZE];

            for (TacticalTrack track : snapshot.tracks()) {
                AdsbMessage msg = trackToAdsbMessage(track, nowMillis);
                AdsbPacketCodec.serializeInto(msg, packetBuffer);

                DatagramPacket datagram = new DatagramPacket(
                        packetBuffer, packetBuffer.length, target
                );
                socket.send(datagram);
            }
        }
    }

    private AdsbMessage trackToAdsbMessage(TacticalTrack track, long nowMillis) {
        short trackId = parseTrackId(track.id());
        double bearingDeg = GeoMath.bearingDegrees(GROUND_STATION, track.position());
        double distanceM = GeoMath.haversineMeters(GROUND_STATION, track.position());

        float azimuth = (float) bearingDeg;

        int altitudeDiffFt = track.altitudeFt() - GROUND_ALTITUDE_FT;
        float elevation = (float) Math.toDegrees(
                Math.atan2(altitudeDiffFt * 0.3048, distanceM)
        );

        float slantRange = (float) Math.sqrt(
                distanceM * distanceM + Math.pow(altitudeDiffFt * 0.3048, 2)
        );

        return new AdsbMessage(
                trackId,
                EMITTER_ID,
                track.callsign(),
                azimuth,
                elevation,
                slantRange,
                track.headingDeg(),
                track.groundSpeedKt(),
                nowMillis,
                STATION_ID
        );
    }

    private static short parseTrackId(String id) {
        try {
            String numeric = id.replaceAll("[^0-9]", "");
            if (numeric.isEmpty()) {
                return 1;
            }
            int value = Integer.parseInt(numeric);
            if (value > 65_535) {
                return (short) 65_535;
            }
            return (short) value;
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    public List<NoFlyZone> zones() {
        return generator.zones();
    }

    private static final class NamedThreadFactory implements ThreadFactory {
        private final String name;

        private NamedThreadFactory(String name) {
            this.name = name;
        }

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, name);
            thread.setDaemon(true);
            return thread;
        }
    }
}