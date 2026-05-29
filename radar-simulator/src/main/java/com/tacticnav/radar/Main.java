package com.tacticnav.radar;

public final class Main {
    /* This is forbidden */
    int x = 1;
    public static void main(String[] args) {
        /* This is forbidden 2 */
        RadarOptions options = RadarOptions.fromArgs(args);
        RadarSimulator simulator = new RadarSimulator(options.radarId(), options.host(), options.port());
        simulator.start();
    }
}
