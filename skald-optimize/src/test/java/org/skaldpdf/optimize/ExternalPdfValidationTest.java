package org.skaldpdf.optimize;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.skaldpdf.codec.RasterImages;
import org.skaldpdf.geom.PageSize;
import org.skaldpdf.image.ImageData;
import org.skaldpdf.pdf.PdfDocument;
import org.skaldpdf.pdf.PdfWriter;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class ExternalPdfValidationTest {
    @Test
    void qpdfAcceptsOptimizedOutputAndLinearizedInputIsUntouched() throws Exception {
        Assumptions.assumeTrue(commandAvailable("qpdf"), "qpdf is not installed");
        var rgb = new byte[700 * 500 * 3];
        new java.util.Random(59).nextBytes(rgb);
        var source = pdfWith(ImageData.fromRgb(700, 500, rgb));
        var optimized = PdfOptimizer.recompress(source,
            new OptimizeOptions(320, 0.75f, true));
        var directory = Files.createTempDirectory("skald-optimize-qpdf-");
        try {
            var optimizedPath = directory.resolve("optimized.pdf");
            Files.write(optimizedPath, optimized);
            assertEquals(0, new ProcessBuilder("qpdf", "--check", optimizedPath.toString())
                .redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start().waitFor());

            var sourcePath = directory.resolve("source.pdf");
            var linearizedPath = directory.resolve("linearized.pdf");
            Files.write(sourcePath, source);
            assertEquals(0, new ProcessBuilder("qpdf", "--linearize", sourcePath.toString(), linearizedPath.toString())
                .redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start().waitFor());
            var linearized = Files.readAllBytes(linearizedPath);
            assertSame(linearized, PdfOptimizer.recompress(linearized, OptimizeOptions.attachments()));
        } finally {
            try (var files = Files.walk(directory)) {
                for (var path : files.sorted(java.util.Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(path);
                }
            }
        }
    }

    private static boolean commandAvailable(String command) {
        try {
            return new ProcessBuilder(command, "--version").redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD).start().waitFor() == 0;
        } catch (java.io.IOException exception) {
            return false;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static byte[] pdfWith(ImageData image) {
        var output = new ByteArrayOutputStream();
        try (var document = new PdfDocument(new PdfWriter(output))) {
            image.drawOn(document, document.addNewPage(PageSize.A4), 20, 20, 555, 800);
        }
        return output.toByteArray();
    }
}
