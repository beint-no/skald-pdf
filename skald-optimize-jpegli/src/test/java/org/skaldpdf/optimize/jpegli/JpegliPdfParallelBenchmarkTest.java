package org.skaldpdf.optimize.jpegli;

import org.junit.jupiter.api.Test;
import org.skaldpdf.geom.PageSize;
import org.skaldpdf.image.ImageData;
import org.skaldpdf.optimize.OptimizeOptions;
import org.skaldpdf.optimize.PdfOptimizer;
import org.skaldpdf.pdf.PdfDocument;
import org.skaldpdf.pdf.PdfWriter;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class JpegliPdfParallelBenchmarkTest {
    @Test
    void recordsFiveDocumentSequentialAndVirtualThreadLatency() throws Exception {
        var sources = new byte[5][];
        for (int index = 0; index < sources.length; index++) {
            var rgb = new byte[1800 * 1300 * 3];
            new Random(101 + index).nextBytes(rgb);
            sources[index] = pdfWith(ImageData.fromRgb(1800, 1300, rgb));
        }
        var options = OptimizeOptions.builder().maxEdge(1200).minimumLosslessBytes(0)
            .minimumSavingsBytes(1).minimumSavingsPercent(0).build();
        var recompressor = new JpegliImageRecompressor();
        var expected = sequential(sources, options, recompressor);
        var actual = parallel(sources, options, recompressor);
        for (int index = 0; index < actual.length; index++) {
            assertArrayEquals(expected[index], actual[index]);
        }

        var sequential = samples(5, () -> sequential(sources, options, recompressor));
        var parallel = samples(5, () -> parallel(sources, options, recompressor));
        var report = "JPEGli PDF batch (5 × 1800×1300): sequential " + sequential / 1_000_000
            + " ms, virtual-thread parallel " + parallel / 1_000_000 + " ms, speedup "
            + String.format(java.util.Locale.ROOT, "%.2f", sequential / (double) parallel) + "×\n";
        Files.createDirectories(Path.of("build", "benchmarks"));
        Files.writeString(Path.of("build", "benchmarks", "jpegli-pdf-parallel.md"), report);
    }

    private static long samples(int count, CheckedRunnable action) throws Exception {
        var samples = new long[count];
        for (int index = 0; index < samples.length; index++) {
            var started = System.nanoTime();
            action.run();
            samples[index] = System.nanoTime() - started;
        }
        Arrays.sort(samples);
        return samples[samples.length / 2];
    }

    private static byte[][] sequential(byte[][] sources, OptimizeOptions options,
                                       JpegliImageRecompressor recompressor) {
        var results = new byte[sources.length][];
        for (int index = 0; index < sources.length; index++) {
            results[index] = PdfOptimizer.recompress(sources[index], options, recompressor);
        }
        return results;
    }

    private static byte[][] parallel(byte[][] sources, OptimizeOptions options,
                                     JpegliImageRecompressor recompressor) throws Exception {
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var tasks = Arrays.stream(sources)
                .map(source -> executor.submit(() -> PdfOptimizer.recompress(source, options, recompressor)))
                .toList();
            var results = new byte[sources.length][];
            for (int index = 0; index < results.length; index++) {
                results[index] = tasks.get(index).get();
            }
            return results;
        }
    }

    private static byte[] pdfWith(ImageData image) {
        var output = new ByteArrayOutputStream();
        try (var document = new PdfDocument(new PdfWriter(output))) {
            image.drawOn(document, document.addNewPage(PageSize.A4), 20, 20, 555, 800);
        }
        return output.toByteArray();
    }

    @FunctionalInterface
    private interface CheckedRunnable {
        void run() throws Exception;
    }
}
