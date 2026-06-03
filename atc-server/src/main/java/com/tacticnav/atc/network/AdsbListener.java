package com.tacticnav.atc.network;

import com.tacticnav.atc.domain.RadarInputMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.util.function.Consumer;

/**
 * Listens on the ATC UDP endpoint for ADS-B packets and forwards parsed observations.
 */
public class AdsbListener implements Runnable {
    private static final Logger log = LoggerFactory.getLogger(AdsbListener.class);
    private final String bindAddress;
    private final int port;
    private final AdsbPacketParser parser;
    private final Consumer<RadarInputMessage> handler;
    private final byte[] buffer = new byte[112];

    private volatile DatagramSocket socket;
    private volatile boolean running = false;
    private volatile Throwable lastError = null;

    public AdsbListener(String bindAddress, int port, Consumer<RadarInputMessage> handler) {
        this.bindAddress = bindAddress;
        this.port = port;
        this.handler = handler;
        this.parser = new AdsbPacketParser();
    }

    @Override
    public void run() {
        running = true;
        try (DatagramSocket datagramSocket = new DatagramSocket(null)) {
            InetAddress localAddress = InetAddress.getByName(bindAddress);
            datagramSocket.bind(new InetSocketAddress(localAddress, port));
            socket = datagramSocket;
            datagramSocket.setSoTimeout(500);
            log.info("Listening for ADS-B packets on {}:{}", bindAddress, port);

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
                    String srcAddr = packet.getAddress().getHostAddress() + ":" + packet.getPort();
                    log.info("Received ADS-B packet from {}: trackId={}, azimuth={}°, elevation={}°, range={}m",
                        srcAddr,
                        message.trackId(),
                        message.azimuth(),
                        message.elevation(),
                        message.slantRange());
                    handler.accept(message);
                } catch (AdsbPacketParser.ParseException e) {
                    log.warn("Failed to parse ADS-B packet", e);
                }
            }
        } catch (Exception e) {
            if (running) {
                lastError = e;
                log.error("Fatal error in ADS-B listener", e);
            }
        } finally {
            socket = null;
            running = false;
            log.info("ADS-B listener stopped");
        }
    }

    public void stop() {
        running = false;
        DatagramSocket currentSocket = socket;
        if (currentSocket != null) {
            currentSocket.close();
        }
    }

    public boolean isRunning() {
        return running;
    }

    public Throwable getLastError() {
        return lastError;
    }
}
