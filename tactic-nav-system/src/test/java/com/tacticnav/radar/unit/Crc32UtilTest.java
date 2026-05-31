package com.tacticnav.radar.unit;

import com.tacticnav.radar.Crc32Util;
import org.junit.jupiter.api.Test;

import java.util.zip.CRC32;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Crc32Util — isolated computation without external dependencies.
 */
class Crc32UtilTest {

    @Test
    void computeCrc32_shouldReturnZero_forEmptyBuffer() {
        assertEquals(0, Crc32Util.computeCrc32(new byte[0], 0, 0));
    }

    @Test
    void computeCrc32_shouldMatchJavaCrc32_forKnownInput() {
        byte[] buf = new byte[]{'R', 'D', 0, 1};
        int result = Crc32Util.computeCrc32(buf, 0, buf.length);

        CRC32 reference = new CRC32();
        reference.update(buf, 0, buf.length);
        assertEquals((int) reference.getValue(), result);
    }

    @Test
    void computeCrc32_shouldBeDifferent_forDifferentData() {
        assertNotEquals(
                Crc32Util.computeCrc32(new byte[]{'R', 'D', 0, 1}, 0, 4),
                Crc32Util.computeCrc32(new byte[]{'R', 'D', 0, 2}, 0, 4));
    }
}