package com.tacticnav.radar;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public record RadarOptions(int radarId, String host, int port) {

    public static RadarOptions fromArgs(String[] args) {
        Properties config = loadConfig();
        int radarId = parseInt(args, 0, Integer.parseInt(config.getProperty("radar.default.id")));
        int port = parseInt(args, 1, Integer.parseInt(config.getProperty("radar.default.port")));
        String host = args.length >= 3 ? args[2] : config.getProperty("radar.default.host");
        return new RadarOptions(radarId, host, port);
    }

    private static Properties loadConfig() {
        Properties props = new Properties();
        try (InputStream in = RadarOptions.class.getClassLoader().getResourceAsStream("radar-config.properties")) {
            if (in == null) {
                throw new IllegalStateException("radar-config.properties not found on classpath");
            }
            props.load(in);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load radar-config.properties", e);
        }
        return props;
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
