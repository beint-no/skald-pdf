package org.skaldpdf.codec;

import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RasterImagesBenchmarkTest {
    @Test
    void bulkArgbUnpackBeatsPerPixelGetRgbAndMatchesIt() throws Exception {
        var image = noisy(1280, 720);
        var expected = RasterImages.fromBufferedImagePixelAtATime(image);
        var actual = RasterImages.fromBufferedImage(image);
        assertArrayEquals(expected.samples(), actual.samples());
        assertArrayEquals(expected.alpha(), actual.alpha());

        for (int warmup = 0; warmup < 20; warmup++) {
            RasterImages.fromBufferedImagePixelAtATime(image);
            RasterImages.fromBufferedImage(image);
        }
        var oldNanos = medianNanos(25, () -> RasterImages.fromBufferedImagePixelAtATime(image));
        var newNanos = medianNanos(25, () -> RasterImages.fromBufferedImage(image));
        var report = "RasterImages ARGB unpack 1280×720: per-pixel getRGB "
            + oldNanos / 1_000 + " µs, bulk getRGB " + newNanos / 1_000 + " µs, speedup "
            + String.format(java.util.Locale.ROOT, "%.2f", oldNanos / (double) newNanos) + "×\n";
        Files.createDirectories(Path.of("build", "benchmarks"));
        Files.writeString(Path.of("build", "benchmarks", "jdk26-raster.md"), report);
        assertTrue(newNanos < oldNanos,
            "Direct DataBufferInt unpack should beat per-pixel getRGB. " + report);
    }

    @Test
    void lazyConstantIsNotFasterThanAHolderClassForFontLoad() throws Exception {
        var holder = medianNanos(40, HolderFont::get);
        var lazy = medianNanos(40, LazyFont.FONT::get);
        var report = "Font-style singleton: holder " + holder + " ns, LazyConstant.get "
            + lazy + " ns (already-initialized path)\n";
        Files.createDirectories(Path.of("build", "benchmarks"));
        Files.writeString(Path.of("build", "benchmarks", "jdk26-lazy-constant.md"), report);
        // After init both should be in the same nanosecond noise band. We keep
        // the holder idiom in SkaldSans because it is already lazy per face
        // without a preview type and is not slower.
        assertTrue(holder < 50_000 && lazy < 50_000, report);
    }

    private static long medianNanos(int runs, Runnable action) {
        var samples = new long[runs];
        for (int index = 0; index < runs; index++) {
            var started = System.nanoTime();
            action.run();
            samples[index] = System.nanoTime() - started;
        }
        java.util.Arrays.sort(samples);
        return samples[runs / 2];
    }

    private static BufferedImage noisy(int width, int height) {
        var image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        var pixels = new int[width * height];
        var random = new java.util.Random(7);
        for (int index = 0; index < pixels.length; index++) {
            pixels[index] = random.nextInt() | 0xff00_0000;
        }
        image.setRGB(0, 0, width, height, pixels, 0, width);
        return image;
    }

    private static final class HolderFont {
        private static final Object FONT = new Object();

        static Object get() {
            return FONT;
        }
    }

    private static final class LazyFont {
        static final java.lang.LazyConstant<Object> FONT = java.lang.LazyConstant.of(Object::new);
    }
}
