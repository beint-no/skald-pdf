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
    void combinedResizeAndJpegEncodeBeatsTheFormerTwoPassComposition() throws Exception {
        var source = RasterImages.asJpeg(RasterImages.fromBufferedImage(noisy(1800, 1200)), 0.94f);
        Runnable former = () -> RasterImages.asJpeg(
            RasterImages.scaleToFit(source, 900, 900), 0.80f);
        Runnable combined = () -> RasterImages.asJpeg(source, 900, 900, 0.80f);
        for (int warmup = 0; warmup < 4; warmup++) {
            former.run();
            combined.run();
        }
        var formerNanos = medianNanos(9, former);
        var combinedNanos = medianNanos(9, combined);
        var result = RasterImages.asJpeg(source, 900, 900, 0.80f);
        assertTrue(result.width() <= 900 && result.height() <= 900);
        var report = "RasterImages resize+JPEG 1800×1200 → 900: former two-pass "
            + formerNanos / 1_000 + " µs, combined " + combinedNanos / 1_000 + " µs, speedup "
            + String.format(java.util.Locale.ROOT, "%.2f", formerNanos / (double) combinedNanos) + "×\n";
        Files.createDirectories(Path.of("build", "benchmarks"));
        Files.writeString(Path.of("build", "benchmarks", "jdk26-jpeg-resize.md"), report);
        assertTrue(combinedNanos < formerNanos,
            "Combined resize and encode should avoid the intermediate JPEG decode. " + report);
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
}
