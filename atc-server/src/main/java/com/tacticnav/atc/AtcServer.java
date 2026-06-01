package com.tacticnav.atc;

import com.tacticnav.atc.fusion.TrackFusionOrchestrator;
import com.tacticnav.atc.fusion.TrackFusionEngine;
import com.tacticnav.atc.network.RadarListener;
import com.tacticnav.atc.state.SituationStateStore;

import java.net.InetAddress;
import java.util.*;

/**
 * ATC (Air Traffic Control) Server - Main Application.
 * 
 * Orchestrates all components:
 *   1. Network listener (single UDP input)
 *   2. Track fusion engine (track association + state)
 * 
 * Initialization:
 *   - Load configuration
 *   - Start UDP listener
 *   - Start track fusion orchestrator
 * 
 * Graceful shutdown:
 *   - Stop all threads
 *   - Clean up resources
 * 
 * Configuration file: atc-config.properties
 *   atc.bind.address = local address to bind
 *   atc.listen.port = local UDP port to listen on
 */
public final class AtcServer {
    
    private final List<Thread> threads = new ArrayList<>();
    
    // Components
    private final SituationStateStore stateStore;
    private final TrackFusionEngine trackFusionEngine;
    private final TrackFusionOrchestrator trackFusionOrchestrator;
    private final RadarListener radarListener;
    
    private volatile boolean running = false;

    /**
     * Create ATC server with configuration.
     */
    private AtcServer(AtcConfiguration config) {
        this.stateStore = new SituationStateStore();
        
        this.trackFusionEngine = new TrackFusionEngine();
        
        this.trackFusionOrchestrator = new TrackFusionOrchestrator(
            trackFusionEngine,
            stateStore,
            1000  // message queue size
        );

        this.radarListener = new RadarListener(
            config.bindAddress(),
            config.listenPort(),
            trackFusionOrchestrator::submitMessage
        );
    }

    /**
     * Start the ATC server.
     * Launches all threads and begins processing.
     */
    public void start() {
        running = true;
        System.out.println("====== ATC SERVER STARTING ======");

        Thread radarThread = new Thread(radarListener, "RadarListener");
        threads.add(radarThread);
        radarThread.start();
        System.out.println("Started UDP listener");

        // Start track fusion orchestrator
        Thread trackFusionThread = new Thread(trackFusionOrchestrator, "TrackFusionOrchestrator");
        threads.add(trackFusionThread);
        trackFusionThread.start();
        System.out.println("Started track fusion orchestrator");

        System.out.println("====== ATC SERVER RUNNING ======");
    }

    /**
     * Gracefully stop the ATC server.
     */
    public void stop() {
        if (!running) return;
        
        running = false;
        System.out.println("====== ATC SERVER STOPPING ======");

        // Signal all components to stop
        radarListener.stop();
        trackFusionOrchestrator.stop();

        // Wait for threads to finish (with timeout)
        long startTime = System.currentTimeMillis();
        long timeoutMs = 10000;

        for (Thread t : threads) {
            try {
                long elapsed = System.currentTimeMillis() - startTime;
                long remaining = Math.max(100, timeoutMs - elapsed);
                t.join(remaining);
                if (t.isAlive()) {
                    System.out.printf("Thread %s did not stop in time, interrupting%n", t.getName());
                    t.interrupt();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        System.out.println("====== ATC SERVER STOPPED ======");
    }

    /**
     * Main entry point.
     */
    public static void main(String[] args) {
        try {
            // Load configuration
            AtcConfiguration config = AtcConfiguration.load();
            System.out.println(config);

            // Create and start server
            AtcServer server = new AtcServer(config);
            server.start();

            // Add shutdown hook for graceful termination
            Runtime.getRuntime().addShutdownHook(new Thread(server::stop, "ShutdownHook"));

            // Keep main thread alive
            Thread.currentThread().join();

        } catch (Exception e) {
            System.err.printf("Fatal error: %s%n", e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}

/**
 * Configuration for ATC server.
 * Loaded from atc-config.properties.
 */
final class AtcConfiguration {
    
    private final String bindAddress;
    private final int listenPort;

    private AtcConfiguration(
            String bindAddress,
            int listenPort
    ) {
        this.bindAddress = bindAddress;
        this.listenPort = listenPort;
    }

    public String bindAddress() { return bindAddress; }
    public int listenPort() { return listenPort; }

    @Override
    public String toString() {
        return String.format(
            "AtcConfiguration{bindAddress=%s, listenPort=%d}",
            bindAddress, listenPort
        );
    }

    /**
     * Resolve the bind address: if configured as "0.0.0.0" or "auto",
     * automatically detect the local non-loopback IP (same as RadarSimulator does).
     */
    private static String resolveBindAddress(String configured) throws Exception {
        if ("0.0.0.0".equals(configured) || "auto".equalsIgnoreCase(configured)) {
            return InetAddress.getLocalHost().getHostAddress();
        }
        return configured;
    }

    /**
     * Load configuration from properties and environment.
     */
    public static AtcConfiguration load() throws Exception {
        Properties props = new Properties();
        try (var in = AtcServer.class.getClassLoader().getResourceAsStream("atc-config.properties")) {
            if (in != null) {
                props.load(in);
            }
        }

        String configuredAddress = props.getProperty("atc.bind.address", "0.0.0.0");
        String bindAddress = resolveBindAddress(configuredAddress);
        int listenPort = Integer.parseInt(props.getProperty("atc.listen.port", "15001"));

        return new AtcConfiguration(
            bindAddress, listenPort
        );
    }
}
