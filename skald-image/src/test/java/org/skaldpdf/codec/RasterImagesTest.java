package org.skaldpdf.codec;

import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class RasterImagesTest {
    @Test
    void excludesPremultipliedArgbFromFastPathAndUnpremultipliesStraightSamples() {
        var image = new BufferedImage(2, 1, BufferedImage.TYPE_INT_ARGB_PRE);
        // Set straight ARGB via setRGB (BufferedImage stores premultiplied internally):
        // alpha=128 (0x80), straight R=200, G=100, B=50 -> premultiplied ~(100, 50, 25).
        image.setRGB(0, 0, (0x80 << 24) | (200 << 16) | (100 << 8) | 50);
        image.setRGB(1, 0, (0xff << 24) | (20 << 16) | (40 << 8) | 60);

        assertNull(RasterImages.argbPixels(image, false));

        var data = RasterImages.fromBufferedImage(image);
        var samples = data.samples();
        var alpha = data.alpha();
        assertNotNull(alpha);
        assertEquals(0x80, alpha[0] & 0xff);
        // Straight R must be ~200 (within rounding from 8-bit premultiplication), NOT ~100!
        assertEquals(200, samples[0] & 0xff, 3);
        assertEquals(100, samples[1] & 0xff, 3);
        assertEquals(50, samples[2] & 0xff, 3);
        assertEquals(0xff, alpha[1] & 0xff);
        assertEquals(20, samples[3] & 0xff);
        assertEquals(40, samples[4] & 0xff);
        assertEquals(60, samples[5] & 0xff);
    }

    @Test
    void excludesSubimagesAndStridedRastersFromFastPath() {
        var full = new BufferedImage(10, 10, BufferedImage.TYPE_INT_ARGB);
        assertNotNull(RasterImages.argbPixels(full, false));
        for (int y = 0; y < 10; y++) {
            for (int x = 0; x < 10; x++) {
                full.setRGB(x, y, (0xff << 24) | (x * 20 << 16) | (y * 20 << 8) | 99);
            }
        }
        var sub = full.getSubimage(3, 4, 4, 3);
        assertNull(RasterImages.argbPixels(sub, false));

        var data = RasterImages.fromBufferedImage(sub);
        assertEquals(4, data.width());
        assertEquals(3, data.height());
        var samples = data.samples();
        // Top-left pixel of subimage corresponds to (3, 4) in full image -> RGB (60, 80, 99).
        assertEquals(60, samples[0] & 0xff);
        assertEquals(80, samples[1] & 0xff);
        assertEquals(99, samples[2] & 0xff);
    }
}
