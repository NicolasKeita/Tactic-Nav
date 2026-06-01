package com.tacticnav.cockpit.data;

import com.tacticnav.cockpit.domain.NoFlyZone;
import com.tacticnav.cockpit.domain.TacticalSnapshot;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public final class UdpAtcTrackSource implements AtcTrackSource {
    public static final int DEFAULT_PORT = 16001;

    private final int listenPort;
    private final List<NoFlyZone> zones;
    private final TrackDatagramDecoder decoder;
    private final AtomicBoolean running = new AtomicBoolean(false);

    private DatagramSocket socket;
    private Thread thread;
    private volatile TacticalSnapshot lastSnapshot;

    public UdpAtcTrackSource(int listenPort, List<NoFlyZone> zones, TrackDatagramDecoder decoder) {
        if (listenPort <= 0 || listenPort > 65_535) {
            throw new IllegalArgumentException("listenPort must be a valid UDP port");
        }
        if (zones == null || decoder == null) {
            throw new IllegalArgumentException("zones and decoder cannot be null");
        }
        this.listenPort = listenPort;
        this.zones = zones;
        this.decoder = decoder;
    }

    @Override
    public void start(Listener listener) {
        if (listener == null) {
            throw new IllegalArgumentException("listener cannot be null");
        }
        if (!running.compareAndSet(false, true)) {
            return;
        }
        thread = new Thread(() -> listen(listener), "cockpit-udp-source");
        thread.setDaemon(true);
        thread.start();
    }

    @Override
    public void stop() {
        running.set(false);
        DatagramSocket currentSocket = socket;
        if (currentSocket != null) {
            currentSocket.close();
        }
        Thread currentThread = thread;
        if (currentThread != null) {
            currentThread.interrupt();
        }
        thread = null;
    }

    private void listen(Listener listener) {
        byte[] buffer = new byte[TrackDatagramDecoder.MAX_PACKET_BYTES];
        try (DatagramSocket datagramSocket = new DatagramSocket(null)) {
            datagramSocket.bind(new InetSocketAddress(listenPort));
            datagramSocket.setSoTimeout(500);
            socket = datagramSocket;

            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            while (running.get() && !Thread.currentThread().isInterrupted()) {
                packet.setLength(buffer.length);
                try {
                    datagramSocket.receive(packet);
                    TacticalSnapshot snapshot = decoder.decode(buffer, packet.getLength(), zones);
                    lastSnapshot = snapshot;
                    listener.onSnapshot(snapshot);
                } catch (SocketTimeoutException timeout) {
                    TacticalSnapshot current = lastSnapshot;
                    if (current != null) {
                        listener.onSnapshot(current);
                    }
                } catch (TrackDatagramDecoder.DecodeException malformed) {
                    listener.onSourceError(malformed);
                }
            }
        } catch (IOException error) {
            if (running.get()) {
                listener.onSourceError(error);
            }
        } finally {
            socket = null;
            running.set(false);
        }
    }
}
