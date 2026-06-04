package com.tacticnav.cockpit.data;

import java.util.zip.CRC32;

public final class Crc32Util {
    private Crc32Util() {}

    public static int computeCrc32(byte[] buffer, int offset, int length) {
        CRC32 crc32 = new CRC32();
        crc32.update(buffer, offset, length);
        return (int) crc32.getValue();
    }
}