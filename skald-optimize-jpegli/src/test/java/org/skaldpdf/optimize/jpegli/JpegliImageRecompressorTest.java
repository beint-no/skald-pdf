package org.skaldpdf.optimize.jpegli;

import org.junit.jupiter.api.Test;
import org.skaldpdf.geom.PageSize;
import org.skaldpdf.image.ImageData;
import org.skaldpdf.optimize.OptimizeOptions;
import org.skaldpdf.optimize.PdfOptimizer;
import org.skaldpdf.pdf.PdfDocument;
import org.skaldpdf.pdf.PdfReader;
import org.skaldpdf.pdf.PdfWriter;

import java.io.ByteArrayOutputStream;
import java.util.Random;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JpegliImageRecompressorTest {
    @Test
    void convertsLosslessPdfRasterAndIsIdempotent() {
        var rgb = new byte[900 * 700 * 3];
        new Random(41).nextBytes(rgb);
        var source = pdfWith(ImageData.fromRgb(900, 700, rgb));
        var options = OptimizeOptions.builder()
            .maxEdge(480).minimumLosslessBytes(0)
            .minimumSavingsBytes(1).minimumSavingsPercent(0).build();
        var recompressor = new JpegliImageRecompressor();
        try (var document = new PdfDocument(new PdfReader(source))) {
            var replacement = recompressor.recompress(document.importedImages().getFirst(), options);
            assertTrue(replacement.isPresent());
            assertTrue(replacement.orElseThrow().samples().length < source.length);
        }

        var once = PdfOptimizer.recompress(source, options, recompressor);
        var twice = PdfOptimizer.recompress(once, options, recompressor);

        assertTrue(once.length < source.length, "optimized=" + once.length + " source=" + source.length);
        assertArrayEquals(once, twice);
        try (var document = new PdfDocument(new PdfReader(once))) {
            assertTrue(document.importedImages().getFirst().jpeg());
        }
    }

    @Test
    void oneRecompressorSupportsConcurrentVirtualThreadDocuments() throws Exception {
        var rgb = new byte[1000 * 700 * 3];
        new Random(43).nextBytes(rgb);
        var source = pdfWith(ImageData.fromRgb(1000, 700, rgb));
        var options = OptimizeOptions.builder().maxEdge(640).minimumLosslessBytes(0)
            .minimumSavingsBytes(1).minimumSavingsPercent(0).build();
        var recompressor = new JpegliImageRecompressor();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var tasks = java.util.stream.IntStream.range(0, 20)
                .mapToObj(ignored -> executor.submit(
                    () -> PdfOptimizer.recompress(source, options, recompressor)))
                .toList();
            var expected = tasks.getFirst().get();
            for (var task : tasks) {
                assertArrayEquals(expected, task.get());
            }
        }
    }

    private static byte[] pdfWith(ImageData image) {
        var output = new ByteArrayOutputStream();
        try (var pdf = new PdfDocument(new PdfWriter(output))) {
            image.drawOn(pdf, pdf.addNewPage(PageSize.A4), 20, 20, 555, 800);
        }
        return output.toByteArray();
    }
}
