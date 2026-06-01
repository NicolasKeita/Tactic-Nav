package com.tacticnav.radar;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Main {
    private static final Logger log = LoggerFactory.getLogger(Main.class);

    /**
     * Application entry point. Parses radar configuration from command-line
     * arguments, then starts the radar simulator.
     */
    public static void main(String[] args) {
        try {
            RadarOptions options = RadarOptions.fromArgs(args);
            RadarSimulator simulator = new RadarSimulator(options.radarId(), options.host(), options.port());
            simulator.start();
        } catch (IllegalArgumentException | IllegalStateException e) {
            log.error(e.getMessage(), e);
            System.exit(1);
        }
    }
}
