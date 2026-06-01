package com.tacticnav.radar;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public record RadarOptions(int radarId, String host, int port) {
    static final int MIN_ARGUMENTS = 3;
    static final String USAGE = """
            Usage: RadarSimulator <radarId> <host> <port>
              radarId  : radar identifier (integer, default if invalid: radar-config.properties)
              host     : destination IP address or hostname
              port     : destination UDP port (integer from 1 to 65535)
            """;

    public static RadarOptions fromArgs(String[] args) {
        if (args.length < MIN_ARGUMENTS) {
            throw new IllegalArgumentException("""
                    Missing required arguments: expected at least 3 arguments but got %d.
                    %s""".formatted(args.length, USAGE));
        }
        Properties config = loadConfig();
        int radarId = parseInt(args, 0, Integer.parseInt(config.getProperty("radar.default.id")));
        String host = requireHost(args[1]);
        int port = parsePort(args[2]);
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

    private static String requireHost(String host) {
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("""
                    Invalid host: host must not be blank.
                    %s""".formatted(USAGE));
        }
        return host;
    }

    private static int parsePort(String value) {
        try {
            int port = Integer.parseInt(value);
            if (port < 1 || port > 65535) {
                throw new IllegalArgumentException("""
                        Invalid port '%s': expected an integer from 1 to 65535.
                        %s""".formatted(value, USAGE));
            }
            return port;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("""
                    Invalid port '%s': expected an integer from 1 to 65535.
                    %s""".formatted(value, USAGE));
        }
    }
}
