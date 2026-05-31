package com.tacticnav.radar.unit;

import com.tacticnav.radar.RadarOptions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for RadarOptions — parsing logic in isolation.
 */
class RadarOptionsTest {

    @Test
    void fromArgs_empty_shouldUseDefaults() {
        RadarOptions opts = RadarOptions.fromArgs(new String[0]);
        assertEquals(1, opts.radarId());
        assertEquals(5001, opts.port());
        assertEquals("127.0.0.1", opts.host());
    }

    @Test
    void fromArgs_allArgs_shouldUseProvidedValues() {
        RadarOptions opts = RadarOptions.fromArgs(new String[]{"5", "9000", "192.168.1.100"});
        assertEquals(5, opts.radarId());
        assertEquals(9000, opts.port());
        assertEquals("192.168.1.100", opts.host());
    }

    @Test
    void fromArgs_invalidRadarId_shouldFallbackToDefault() {
        RadarOptions opts = RadarOptions.fromArgs(new String[]{"not-a-number"});
        assertEquals(1, opts.radarId());
    }

    @Test
    void fromArgs_invalidPort_shouldFallbackToDefault() {
        RadarOptions opts = RadarOptions.fromArgs(new String[]{"7", "invalid-port"});
        assertEquals(7, opts.radarId());
        assertEquals(5001, opts.port());
    }
}