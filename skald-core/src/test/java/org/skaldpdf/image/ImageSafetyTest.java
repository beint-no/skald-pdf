package org.skaldpdf.image;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImageSafetyTest {
    @Test
    void rejectsUnknownBytesBeforeDecoding() {
        var error = assertThrows(IllegalArgumentException.class,
            () -> ImageDataFactory.create(new byte[] {1, 2, 3, 4, 5, 6, 7, 8}));
        assertTrue(error.getMessage().contains("Unsupported"));
    }

    @Test
    void rejectsDeclaredPixelCountsBeforeImageIoAllocates() {
        var png = new byte[24];
        png[0] = (byte) 0x89;
        png[1] = 0x50;
        png[2] = 0x4e;
        png[3] = 0x47;
        png[4] = 0x0d;
        png[5] = 0x0a;
        png[6] = 0x1a;
        png[7] = 0x0a;
        png[16] = 0x00;
        png[17] = 0x01;
        png[18] = (byte) 0x86;
        png[19] = (byte) 0xa0;
        png[20] = 0x00;
        png[21] = 0x00;
        png[22] = 0x27;
        png[23] = 0x10;
        var error = assertThrows(IllegalArgumentException.class, () -> ImageDataFactory.create(png));
        assertTrue(error.getMessage().toLowerCase().contains("dimension"));
    }

    @Test
    void rejectsOversizedEncodedPayloads() {
        var bytes = new byte[32 * 1024 * 1024 + 1];
        bytes[0] = (byte) 0xff;
        bytes[1] = (byte) 0xd8;
        assertThrows(IllegalArgumentException.class, () -> ImageDataFactory.create(bytes));
    }
}
