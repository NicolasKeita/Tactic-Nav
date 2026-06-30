package com.tacticnav.atc.config;

import java.io.InputStream;
import java.net.InetAddress;
import java.util.Properties;

/**
 * Configuration for ATC server.
 * Loaded from atc-config.properties file in the classpath.
 * 
 * Configuration properties:
 *   - atc.bind.address: local address to bind (default: 0.0.0.0)
 *   - atc.listen.port: local UDP port to listen on (default: 15001)
 */
public final class AtcConfiguration {
    
    private final String bindAddress;
    private final int listenPort;

    /**
     * Create ATC configuration with specified parameters.
     * 
     * @param bindAddress the address to bind to
     * @param listenPort the UDP port to listen on
     * @throws IllegalArgumentException if port is not valid (1-65535)
     */
    public AtcConfiguration(String bindAddress, int listenPort) {
        if (bindAddress == null || bindAddress.isEmpty()) {
            throw new IllegalArgumentException("bindAddress cannot be null or empty");
        }
        if (listenPort < 1 || listenPort > 65535) {
            throw new IllegalArgumentException("listenPort must be between 1 and 65535");
        }
        this.bindAddress = bindAddress;
        this.listenPort = listenPort;
    }

    /**
     * Get the bind address.
     */
    public String bindAddress() {
        return bindAddress;
    }

    /**
     * Get the listen port.
     */
    public int listenPort() {
        return listenPort;
    }

    @Override
    public String toString() {
        return String.format(
            "AtcConfiguration{bindAddress=%s, listenPort=%d}",
            bindAddress, listenPort
        );
    }

    /**
     * Resolve the bind address: if configured as "0.0.0.0" or "auto",
     * automatically detect the local non-loopback IP address.
     * 
     * @param configured the configured address
     * @return the resolved IP address
     * @throws Exception if address resolution fails
     */
    static String resolveBindAddress(String configured) throws Exception {
        if ("0.0.0.0".equals(configured) || "auto".equalsIgnoreCase(configured)) {
            return InetAddress.getLocalHost().getHostAddress();
        }
        return configured;
    }

    /**
     * Load configuration from properties file.
     * Looks for atc-config.properties in the classpath.
     * 
     * @return loaded configuration
     * @throws Exception if configuration cannot be loaded or is invalid
     */
    public static AtcConfiguration load() throws Exception {
        Properties props = new Properties();
        try (InputStream in = AtcConfiguration.class.getClassLoader()
                .getResourceAsStream("atc-config.properties")) {
            if (in != null) {
                props.load(in);
            }
        }

        String configuredAddress = props.getProperty("atc.bind.address", "0.0.0.0");
        String bindAddress = resolveBindAddress(configuredAddress);
        int listenPort = Integer.parseInt(
            props.getProperty("atc.listen.port", "15001")
        );

        return new AtcConfiguration(bindAddress, listenPort);
    }

    /**
     * Create default configuration.
     * 
     * @return configuration with default values (0.0.0.0:15001)
     */
    public static AtcConfiguration defaults() {
        return new AtcConfiguration("0.0.0.0", 15001);
    }
}
