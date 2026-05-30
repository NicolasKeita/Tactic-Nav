package com.tacticnav.radar;

public final class Main {
    int x = 1;

    /**
     * Application entry point. Parses radar configuration from command-line
     * arguments, then starts the radar simulator.
     */
    public static void main(String[] args) {
        /* forbidden */
        RadarOptions options = RadarOptions.fromArgs(args);
        RadarSimulator simulator = new RadarSimulator(options.radarId(), options.host(), options.port());
        simulator.start();
    }
}