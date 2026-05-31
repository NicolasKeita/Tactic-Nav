package com.tacticnav.atc;

import com.tacticnav.atc.broadcast.BroadcastService;
import com.tacticnav.atc.fusion.FusionOrchestrator;
import com.tacticnav.atc.fusion.TrackFusionEngine;
import com.tacticnav.atc.network.RadarListener;
import com.tacticnav.atc.state.SituationStateStore;

import java.util.*;

/**
 * ATC (Air Traffic Control) Server - Main Application.
 * 
 * Orchestrates all components:
 *   1. Network listeners (UDP per radar source)
 *   2. Fusion engine (track association + state)
 *   3. Broadcast service (state to cockpits)
 * 
 * Initialization:
 *   - Load configuration
 *   - Start radar listeners
 *   - Start fusion orchestrator
 *   - Start broadcast service
 * 
 * Graceful shutdown:
 *   - Stop all threads
 *   - Clean up resources
 * 
 * Configuration file: radar-config.properties
 *   atc.radar.ports = comma-separated UDP port list
 *   atc.radar.lat = radar reference latitude
 *   atc.radar.lon = radar reference longitude
 *   atc.radar.alt = radar reference altitude
 *   atc.broadcast.port = UDP source port for cockpit broadcasts
 *   atc.broadcast.interval = broadcast interval (ms)
 *   atc.cockpit.address = cockpit client address (ip:port)
 */
public final class AtcServer {
    
    private final List<Thread> threads = new ArrayList<>();
    
    // Components
    private final SituationStateStore stateStore;
    private final TrackFusionEngine fusionEngine;
    private final FusionOrchestrator fusionOrchestrator;
    private final BroadcastService broadcastService;
    private final List<RadarListener> radarListeners = new ArrayList<>();
    
    private volatile boolean running = false;

    /**
     * Create ATC server with configuration.
     */
    private AtcServer(AtcConfiguration config) {
        this.stateStore = new SituationStateStore();
        
        this.fusionEngine = new TrackFusionEngine(
            config.radarLatitude(),
            config.radarLongitude(),
            config.radarAltitude()
        );
        
        this.fusionOrchestrator = new FusionOrchestrator(
            fusionEngine,
            stateStore,
            1000  // message queue size
        );
        
        this.broadcastService = new BroadcastService(
            stateStore,
            config.broadcastPort(),
            config.broadcastIntervalMs()
        );
        
        // Register broadcast service as observer
        fusionOrchestrator.addObserver(broadcastService);

        // Create radar listeners
        int radarId = 1;
        for (int port : config.radarPorts()) {
            RadarListener listener = new RadarListener(
                radarId,
                port,
                fusionOrchestrator::submitMessage
            );
            radarListeners.add(listener);
            radarId++;
        }

        // Register cockpit clients
        for (BroadcastService.ClientAddress client : config.cockpitClients()) {
            broadcastService.addClient(client);
        }
    }

    /**
     * Start the ATC server.
     * Launches all threads and begins processing.
     */
    public void start() {
        running = true;
        System.out.println("====== ATC SERVER STARTING ======");

        for (int i = 0; i < radarListeners.size(); i++) {
            RadarListener listener = radarListeners.get(i);
            Thread t = new Thread(listener, "RadarListener-" + (i + 1));
            threads.add(t);
            t.start();
        }
        System.out.printf("Started %d radar listeners%n", radarListeners.size());

        // Start fusion orchestrator
        Thread fusionThread = new Thread(fusionOrchestrator, "FusionOrchestrator");
        threads.add(fusionThread);
        fusionThread.start();
        System.out.println("Started fusion orchestrator");

        // Start broadcast service
        Thread broadcastThread = new Thread(broadcastService, "BroadcastService");
        threads.add(broadcastThread);
        broadcastThread.start();
        System.out.println("Started broadcast service");

        // Start monitoring thread
        Thread monitorThread = new Thread(this::runMonitoring, "Monitoring");
        threads.add(monitorThread);
        monitorThread.start();

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
        radarListeners.forEach(RadarListener::stop);
        fusionOrchestrator.stop();
        broadcastService.stop();

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
     * Monitoring loop: prints statistics periodically.
     */
    private void runMonitoring() {
        try {
            while (running) {
                Thread.sleep(5000);  // Print stats every 5 seconds
                
                FusionOrchestrator.FusionStats stats = fusionOrchestrator.getStats();
                System.out.printf("[MONITOR] %s, broadcasts=%d%n", stats, broadcastService.getBroadcastsSent());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
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
 * Loaded from radar-config.properties.
 */
final class AtcConfiguration {
    
    private final List<Integer> radarPorts;
    private final double radarLatitude;
    private final double radarLongitude;
    private final double radarAltitude;
    private final int broadcastPort;
    private final long broadcastIntervalMs;
    private final List<BroadcastService.ClientAddress> cockpitClients;

    private AtcConfiguration(
            List<Integer> radarPorts,
            double radarLat,
            double radarLon,
            double radarAlt,
            int broadcastPort,
            long broadcastInterval,
            List<BroadcastService.ClientAddress> clients
    ) {
        this.radarPorts = radarPorts;
        this.radarLatitude = radarLat;
        this.radarLongitude = radarLon;
        this.radarAltitude = radarAlt;
        this.broadcastPort = broadcastPort;
        this.broadcastIntervalMs = broadcastInterval;
        this.cockpitClients = clients;
    }

    public List<Integer> radarPorts() { return radarPorts; }
    public double radarLatitude() { return radarLatitude; }
    public double radarLongitude() { return radarLongitude; }
    public double radarAltitude() { return radarAltitude; }
    public int broadcastPort() { return broadcastPort; }
    public long broadcastIntervalMs() { return broadcastIntervalMs; }
    public List<BroadcastService.ClientAddress> cockpitClients() { return cockpitClients; }

    @Override
    public String toString() {
        return String.format(
            "AtcConfiguration{radarPorts=%s, radarLat=%.6f, radarLon=%.6f, radarAlt=%.1f, " +
            "broadcastPort=%d, broadcastInterval=%dms, clients=%d}",
            radarPorts, radarLatitude, radarLongitude, radarAltitude,
            broadcastPort, broadcastIntervalMs, cockpitClients.size()
        );
    }

    /**
     * Load configuration from properties and environment.
     */
    public static AtcConfiguration load() throws Exception {
        Properties props = new Properties();
        try (var in = AtcServer.class.getClassLoader().getResourceAsStream("radar-config.properties")) {
            if (in != null) {
                props.load(in);
            }
        }

        // Parse radar ports
        String portStr = props.getProperty("atc.radar.ports", "15001,15002,15003");
        List<Integer> ports = new ArrayList<>();
        for (String p : portStr.split(",")) {
            ports.add(Integer.parseInt(p.trim()));
        }

        // Parse coordinates
        double radarLat = Double.parseDouble(props.getProperty("atc.radar.lat", "40.7128"));
        double radarLon = Double.parseDouble(props.getProperty("atc.radar.lon", "-74.0060"));
        double radarAlt = Double.parseDouble(props.getProperty("atc.radar.alt", "100.0"));

        // Parse broadcast
        int broadcastPort = Integer.parseInt(props.getProperty("atc.broadcast.port", "15000"));
        long broadcastInterval = Long.parseLong(props.getProperty("atc.broadcast.interval", "100"));

        // Parse cockpit clients
        List<BroadcastService.ClientAddress> clients = new ArrayList<>();
        String clientStr = props.getProperty("atc.cockpit.addresses", "127.0.0.1:16000");
        for (String addr : clientStr.split(";")) {
            String[] parts = addr.trim().split(":");
            if (parts.length == 2) {
                clients.add(new BroadcastService.ClientAddress(parts[0], Integer.parseInt(parts[1])));
            }
        }

        return new AtcConfiguration(
            ports, radarLat, radarLon, radarAlt,
            broadcastPort, broadcastInterval, clients
        );
    }
}
