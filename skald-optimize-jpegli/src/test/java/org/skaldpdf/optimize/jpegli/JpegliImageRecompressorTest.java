package org.skaldpdf.optimize.jpegli;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.skaldpdf.geom.PageSize;
import org.skaldpdf.image.ImageData;
import org.skaldpdf.optimize.OptimizeOptions;
import org.skaldpdf.optimize.PdfOptimizer;
import org.skaldpdf.pdf.PdfDocument;
import org.skaldpdf.pdf.PdfReader;
import org.skaldpdf.pdf.PdfWriter;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Random;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
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

    @Test
    void preservesTheDimensionsOfAnImageWithASoftMask() {
        var rgb = new byte[900 * 700 * 3];
        var alpha = new byte[900 * 700];
        new Random(47).nextBytes(rgb);
        for (var index = 0; index < alpha.length; index++) {
            alpha[index] = (byte) (index % 251);
        }
        var source = pdfWith(ImageData.fromRgb(900, 700, rgb, alpha));
        var options = OptimizeOptions.builder().maxEdge(480).minimumLosslessBytes(0)
            .minimumSavingsBytes(1).minimumSavingsPercent(0).build();
        var recompressor = new JpegliImageRecompressor();

        var optimized = PdfOptimizer.recompress(source, options, recompressor);

        assertTrue(optimized.length < source.length);
        assertArrayEquals(optimized, PdfOptimizer.recompress(optimized, options, recompressor));
        try (var document = new PdfDocument(new PdfReader(optimized))) {
            var image = document.importedImages().getFirst();
            assertTrue(image.jpeg());
            assertTrue(image.requiresOriginalDimensions());
            assertEquals(900, image.width());
            assertEquals(700, image.height());
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {3, 6})
    void preservesPdfPixelOrientationWhenEmbeddedJpegHasExifOrientation(int orientation) throws Exception {
        var jpeg = injectExifOrientation(quadrantJpeg(600, 300), orientation);
        var source = pdfWith(ImageData.fromJpeg(jpeg));
        var options = OptimizeOptions.builder()
            .maxEdge(600).jpegQuality(0.70f)
            .minimumSavingsBytes(1).minimumSavingsPercent(0).build();
        var recompressor = new JpegliImageRecompressor();

        var optimized = PdfOptimizer.recompress(source, options, recompressor);

        assertTrue(optimized.length < source.length, "optimized=" + optimized.length + " source=" + source.length);
        try (var document = new PdfDocument(new PdfReader(optimized))) {
            var image = document.importedImages().getFirst();
            assertEquals(600, image.width());
            assertEquals(300, image.height());
        }
        assertRenderedVisuallyMatches(source, optimized);
    }

    @Test
    void preservesPdfPixelOrientationWhenDownscalingExifRotatedJpeg() throws Exception {
        var jpeg = injectExifOrientation(quadrantJpeg(800, 400), 6);
        var source = pdfWith(ImageData.fromJpeg(jpeg));
        var options = OptimizeOptions.builder()
            .maxEdge(400).jpegQuality(0.75f)
            .minimumSavingsBytes(1).minimumSavingsPercent(0).build();
        var recompressor = new JpegliImageRecompressor();

        var optimized = PdfOptimizer.recompress(source, options, recompressor);

        assertTrue(optimized.length < source.length);
        try (var document = new PdfDocument(new PdfReader(optimized))) {
            var image = document.importedImages().getFirst();
            assertEquals(400, image.width());
            assertEquals(200, image.height());
        }
        assertRenderedVisuallyMatches(source, optimized);
    }

    private static void assertRenderedVisuallyMatches(byte[] expectedPdf, byte[] actualPdf) throws Exception {
        try (var expectedDoc = Loader.loadPDF(expectedPdf);
             var actualDoc = Loader.loadPDF(actualPdf)) {
            var expected = new PDFRenderer(expectedDoc).renderImageWithDPI(0, 72, ImageType.RGB);
            var actual = new PDFRenderer(actualDoc).renderImageWithDPI(0, 72, ImageType.RGB);
            assertEquals(expected.getWidth(), actual.getWidth());
            assertEquals(expected.getHeight(), actual.getHeight());
            long totalDiff = 0;
            var pixels = (long) expected.getWidth() * expected.getHeight();
            for (int y = 0; y < expected.getHeight(); y++) {
                for (int x = 0; x < expected.getWidth(); x++) {
                    var e = expected.getRGB(x, y);
                    var a = actual.getRGB(x, y);
                    totalDiff += Math.abs(((e >>> 16) & 0xff) - ((a >>> 16) & 0xff))
                        + Math.abs(((e >>> 8) & 0xff) - ((a >>> 8) & 0xff))
                        + Math.abs((e & 0xff) - (a & 0xff));
                }
            }
            var meanChannelDiff = totalDiff / (double) (pixels * 3);
            assertTrue(meanChannelDiff < 8.0, "Rendered pixels diverged (mean channel diff=" + meanChannelDiff + ")");
        }
    }

    private static byte[] quadrantJpeg(int width, int height) throws Exception {
        var image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        var random = new Random(19);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                var jitter = random.nextInt(19) - 9;
                int r;
                int g;
                int b;
                if (y < height / 2 && x < width / 2) {
                    r = 225; g = 35; b = 35;
                } else if (y < height / 2) {
                    r = 35; g = 210; b = 45;
                } else if (x < width / 2) {
                    r = 35; g = 55; b = 225;
                } else {
                    r = 230; g = 215; b = 35;
                }
                r = Math.clamp(r + jitter, 0, 255);
                g = Math.clamp(g + jitter, 0, 255);
                b = Math.clamp(b + jitter, 0, 255);
                image.setRGB(x, y, (r << 16) | (g << 8) | b);
            }
        }
        var output = new ByteArrayOutputStream();
        var writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        try (var stream = new MemoryCacheImageOutputStream(output)) {
            var params = writer.getDefaultWriteParam();
            params.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            params.setCompressionQuality(0.98f);
            writer.setOutput(stream);
            writer.write(null, new IIOImage(image, null, null), params);
        } finally {
            writer.dispose();
        }
        return output.toByteArray();
    }

    private static byte[] injectExifOrientation(byte[] jpeg, int orientation) {
        assertEquals((byte) 0xff, jpeg[0]);
        assertEquals((byte) 0xd8, jpeg[1]);
        byte[] app1 = {
            (byte) 0xff, (byte) 0xe1,
            0x00, 0x22,
            'E', 'x', 'i', 'f', 0x00, 0x00,
            'M', 'M', 0x00, 0x2a,
            0x00, 0x00, 0x00, 0x08,
            0x00, 0x01,
            0x01, 0x12, 0x00, 0x03, 0x00, 0x00, 0x00, 0x01, 0x00, (byte) orientation, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00
        };
        var result = new byte[jpeg.length + app1.length];
        result[0] = jpeg[0];
        result[1] = jpeg[1];
        System.arraycopy(app1, 0, result, 2, app1.length);
        System.arraycopy(jpeg, 2, result, 2 + app1.length, jpeg.length - 2);
        return result;
    }

    private static byte[] pdfWith(ImageData image) {
        var output = new ByteArrayOutputStream();
        try (var pdf = new PdfDocument(new PdfWriter(output))) {
            image.drawOn(pdf, pdf.addNewPage(PageSize.A4), 20, 20, 555, 800);
        }
        return output.toByteArray();
    }
}
