package com.tacticnav.radar;

public record RadarOptions(int radarId, String host, int port) {

    public static RadarOptions fromArgs(String[] args) {
        int radarId = parseInt(args, 0, 1);
        int port = parseInt(args, 1, 5001);
        String host = args.length >= 3 ? args[2] : "127.0.0.1";
        return new RadarOptions(radarId, host, port);
    }

    private static int parseInt(String[] args, int index, int defaultValue) {
        if (args.length <= index) {
            return defaultValue;
        }

        try {
            return Integer.parseInt(args[index]);
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }
}
