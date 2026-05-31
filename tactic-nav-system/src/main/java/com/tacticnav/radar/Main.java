package com.tacticnav.radar;

public final class Main {
    /**
     * Application entry point. Parses radar configuration from command-line
     * arguments, then starts the radar simulator.
     */
    public static void main(String[] args) {
        RadarOptions options = RadarOptions.fromArgs(args);
        RadarSimulator simulator = new RadarSimulator(options.radarId(), options.host(), options.port());
        simulator.start();
    }
}
