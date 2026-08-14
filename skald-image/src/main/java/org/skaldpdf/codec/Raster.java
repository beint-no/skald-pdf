package org.skaldpdf.codec;

import java.util.Objects;

/** Packed 8-bit RGB, 3 bytes per pixel, top-down. */
public record Raster(int width, int height, byte[] rgb) {
    public Raster {
        if (width < 1 || height < 1) {
            throw new IllegalArgumentException("Raster dimensions must be positive");
        }
        Objects.requireNonNull(rgb, "rgb");
        var expected = Math.multiplyExact(Math.multiplyExact(width, height), 3);
        if (rgb.length != expected) {
            throw new IllegalArgumentException("RGB buffer must be width*height*3 bytes");
        }
    }
}
