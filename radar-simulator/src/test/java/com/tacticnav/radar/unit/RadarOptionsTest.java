package com.tacticnav.radar.unit;

import com.tacticnav.radar.RadarOptions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for RadarOptions — parsing logic in isolation.
 */
class RadarOptionsTest {

    @Test
    void fromArgs_empty_shouldRejectMissingArguments() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> RadarOptions.fromArgs(new String[0]));

        assertTrue(error.getMessage().contains("expected at least 3 arguments but got 0"));
        assertTrue(error.getMessage().contains("Usage: RadarSimulator <radarId> <host> <port>"));
    }

    @Test
    void fromArgs_twoArgs_shouldRejectMissingPort() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> RadarOptions.fromArgs(new String[]{"1", "192.168.56.1"}));

        assertTrue(error.getMessage().contains("expected at least 3 arguments but got 2"));
        assertTrue(error.getMessage().contains("port"));
    }

    @Test
    void fromArgs_allArgs_shouldUseProvidedValues() {
        RadarOptions opts = RadarOptions.fromArgs(new String[]{"5", "192.168.1.100", "9000"});
        assertEquals(5, opts.radarId());
        assertEquals(9000, opts.port());
        assertEquals("192.168.1.100", opts.host());
    }

    @Test
    void fromArgs_invalidRadarId_shouldFallbackToDefault() {
        RadarOptions opts = RadarOptions.fromArgs(new String[]{"not-a-number", "192.168.1.100", "9000"});
        assertEquals(1, opts.radarId());
        assertEquals(9000, opts.port());
        assertEquals("192.168.1.100", opts.host());
    }

    @Test
    void fromArgs_invalidPort_shouldRejectWithUsage() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> RadarOptions.fromArgs(new String[]{"7", "192.168.1.100", "invalid-port"}));

        assertTrue(error.getMessage().contains("Invalid port 'invalid-port'"));
        assertTrue(error.getMessage().contains("Usage: RadarSimulator <radarId> <host> <port>"));
    }

    @Test
    void fromArgs_outOfRangePort_shouldRejectWithUsage() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> RadarOptions.fromArgs(new String[]{"7", "192.168.1.100", "70000"}));

        assertTrue(error.getMessage().contains("Invalid port '70000'"));
        assertTrue(error.getMessage().contains("1 to 65535"));
    }

    @Test
    void fromArgs_blankHost_shouldRejectWithUsage() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> RadarOptions.fromArgs(new String[]{"7", " ", "15002"}));

        assertTrue(error.getMessage().contains("Invalid host"));
        assertTrue(error.getMessage().contains("host must not be blank"));
    }
}
