package com.tacticnav.cockpit;

/**
 * Centralized constants for the cockpit application.
 * This class contains all shared configuration keys, default values,
 * and other constants used across multiple classes.
 */
public final class CockpitConstants {
    
    // SharedPreferences keys
    public static final String PREFS_NAME = "cockpit_prefs";
    public static final String PREFS_HOST = "adsb_host";
    public static final String PREFS_PORT = "adsb_port";
    public static final String PREFS_CONFIGURED = "adsb_configured";
    
    // Default values
    public static final String DEFAULT_HOST = "192.168.1.109";
    public static final String DEFAULT_PORT = "9876";
    public static final int DEFAULT_PORT_INT = 9876;
    
    // Track source metadata key
    public static final String TRACK_SOURCE_KEY = "com.tacticnav.cockpit.TRACK_SOURCE";
    
    // Track source mode values
    public static final String UDP_SOURCE = "UDP";
    public static final String ADSB_SOURCE = "ADSB";
    public static final String SIMULATED_SOURCE = "SIMULATED";
    
    // Frame timing
    public static final long FRAME_INTERVAL_NANOS = 33_333_333L; // ~30 FPS
    
    // Default viewport coordinates (Toulouse area)
    public static final double VIEWPORT_CENTER_LAT = 43.8915;
    public static final double VIEWPORT_CENTER_LON = -0.5007;
    public static final double VIEWPORT_LAT_SPAN = 0.245;
    public static final double VIEWPORT_LON_SPAN = 0.360;
    
    private CockpitConstants() {
        // Prevent instantiation
    }
}
