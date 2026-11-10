package org.skaldpdf.optimize.jxl;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.skaldpdf.codec.JpegXlOptions;
import org.skaldpdf.codec.NativeImages;
import org.skaldpdf.codec.RasterImages;
import org.skaldpdf.geom.PageSize;
import org.skaldpdf.image.ImageData;
import org.skaldpdf.image.JpegXlCodecs;
import org.skaldpdf.optimize.OptimizeOptions;
import org.skaldpdf.optimize.PdfOptimizer;
import org.skaldpdf.pdf.*;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

class JpegXlOptimizerTest {
    @BeforeAll
    static void requireCodec() {
        if ("1".equals(System.getenv("SKALD_REQUIRE_JXL_TESTS")))
            assertTrue(NativeImages.jpegXlEncodingAvailable());
        assumeTrue(NativeImages.jpegXlEncodingAvailable());
    }

    static ImageData pixels(int width, int height) {
        var rgb = new byte[width * height * 3];
        for (int y = 0; y < height; y++)
            for (int x = 0; x < width; x++) {
                var i = (y * width + x) * 3;
                rgb[i] = (byte) (x * 255 / width);
                rgb[i + 1] = (byte) (y * 255 / height);
                rgb[i + 2] = (byte) ((x + y) % 256);
            }
        return ImageData.fromRgb(width, height, rgb);
    }

    static byte[] pdf(ImageData image, boolean experimental) {
        var output = new ByteArrayOutputStream();
        var properties = WriterProperties.defaults();
        if (experimental) properties = properties.withExperimentalJpegXl();
        try (var document = new PdfDocument(new PdfWriter(output, properties))) {
            var page = document.addNewPage(new PageSize(160, 120));
            image.drawOn(document, page, 0, 0, 160, 120);
        }
        return output.toByteArray();
    }

    static OptimizeOptions options() {
        return OptimizeOptions.builder()
                .experimentalJpegXl(true)
                .minimumSavingsBytes(1)
                .minimumSavingsPercent(0)
                .minimumLosslessBytes(0)
                .removeTrueTypeHinting(false)
                .removeUnusedGlyphs(false)
                .downsampleAbovePixelsPerInch(0)
                .build();
    }

    @Test
    void requiresExplicitOptIn() {
        var encoded = NativeImages.jpegXlEncode(pixels(8, 8), JpegXlOptions.lossless());
        assertThrows(IllegalArgumentException.class, () -> pdf(encoded, false));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        PdfOptimizer.recompress(
                                pdf(pixels(8, 8), false),
                                OptimizeOptions.attachments(),
                                new JpegXlImageRecompressor()));
    }

    @Test
    void rewritesAnUploadedJpegAndKeepsExactReconstruction(@TempDir Path directory)
            throws Exception {
        var jpeg = RasterImages.asJpeg(pixels(256, 192), 0.9f);
        var source = pdf(jpeg, false);
        var output = PdfOptimizer.recompress(source, options(), new JpegXlImageRecompressor());
        assertTrue(output.length < source.length);
        try (var document = new PdfDocument(new PdfReader(output))) {
            var image = document.importedImages().getFirst();
            assertTrue(image.jpegXl());
            assertTrue(image.decode().isPresent());
            var jxl = directory.resolve("image.jxl");
            var reconstructed = directory.resolve("image.jpg");
            Files.write(jxl, image.encodedBytes());
            var process =
                    new ProcessBuilder("djxl", jxl.toString(), reconstructed.toString())
                            .redirectErrorStream(true)
                            .start();
            var log = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            assertEquals(0, process.waitFor(), log);
            assertArrayEquals(jpeg.samples(), Files.readAllBytes(reconstructed));
        }
        assertArrayEquals(
                output, PdfOptimizer.recompress(output, options(), new JpegXlImageRecompressor()));
    }

    @Test
    void roundTripsLosslessRgbGrayAndExternalSoftMask() {
        for (var image :
                new ImageData[] {
                    pixels(128, 128), ImageData.fromGray(128, 128, new byte[128 * 128])
                }) {
            var encoded = NativeImages.jpegXlEncode(image, JpegXlOptions.lossless());
            var output = pdf(encoded, true);
            try (var document = new PdfDocument(new PdfReader(output))) {
                assertArrayEquals(
                        image.samples(),
                        document.importedImages().getFirst().decode().orElseThrow().samples());
            }
        }
        var rgb = pixels(128, 128);
        var alpha = new byte[128 * 128];
        Arrays.fill(alpha, (byte) 128);
        var source = pdf(ImageData.fromRgb(128, 128, rgb.samples(), alpha), false);
        var output = PdfOptimizer.recompress(source, options(), new JpegXlImageRecompressor());
        try (var document = new PdfDocument(new PdfReader(output))) {
            var image = document.importedImages().getFirst();
            assertTrue(image.jpegXl());
            assertArrayEquals(
                    alpha,
                    image.withResampledSoftMask()
                            .orElseThrow()
                            .decodeSoftMask()
                            .orElseThrow()
                            .samples());
        }
    }

    @Test
    void resizesRawSamplesAndTheirMaskTogether() {
        var rgb = pixels(512, 512);
        var alpha = new byte[512 * 512];
        Arrays.fill(alpha, (byte) 128);
        var source = pdf(ImageData.fromRgb(512, 512, rgb.samples(), alpha), false);
        var options =
                OptimizeOptions.builder()
                        .experimentalJpegXl(true)
                        .maxEdge(128)
                        .downsampleAbovePixelsPerInch(100)
                        .downsampleToPixelsPerInch(72)
                        .minimumLosslessBytes(0)
                        .minimumSavingsBytes(1)
                        .minimumSavingsPercent(0)
                        .build();
        var output = PdfOptimizer.recompress(source, options, new JpegXlImageRecompressor());
        try (var document = new PdfDocument(new PdfReader(output))) {
            var image = document.importedImages().getFirst();
            assertTrue(image.jpegXl());
            assertEquals(128, image.width());
            assertEquals(128, image.height());
            var mask = image.withResampledSoftMask().orElseThrow().decodeSoftMask().orElseThrow();
            assertEquals(128, mask.width());
            assertEquals(128, mask.height());
            for (var value : mask.samples()) assertEquals((byte) 128, value);
        }
    }

    @Test
    void independentlyRendersLosslessJxlAndPreservedMask(@TempDir Path directory) throws Exception {
        var source = pixels(128, 128);
        var control = pdf(source, false);
        var encoded = NativeImages.jpegXlEncode(source, JpegXlOptions.lossless());
        var input = directory.resolve("input.pdf");
        var normalized = directory.resolve("normalized.pdf");
        Files.write(input, pdf(encoded, true));
        var process =
                new ProcessBuilder(
                                "python3",
                                "scripts/verify-jxl.py",
                                input.toString(),
                                normalized.toString())
                        .redirectErrorStream(true)
                        .start();
        var log = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(0, process.waitFor(), log);
        try (var actual = org.apache.pdfbox.Loader.loadPDF(normalized.toFile());
                var expected = org.apache.pdfbox.Loader.loadPDF(control)) {
            var a = new org.apache.pdfbox.rendering.PDFRenderer(actual).renderImage(0);
            var b = new org.apache.pdfbox.rendering.PDFRenderer(expected).renderImage(0);
            assertArrayEquals(
                    b.getRGB(0, 0, 160, 120, null, 0, 160), a.getRGB(0, 0, 160, 120, null, 0, 160));
        }
    }

    @Test
    void combinesBrotliAndJxlWithoutChangingLosslessPixels(@TempDir Path directory)
            throws Exception {
        assumeTrue(Compression.BROTLI_BALANCED.available());
        var source = pixels(128, 128);
        var encoded = NativeImages.jpegXlEncode(source, JpegXlOptions.lossless());
        var output = new ByteArrayOutputStream();
        try (var document =
                new PdfDocument(
                        new PdfWriter(
                                output,
                                new WriterProperties(Compression.BROTLI_BALANCED)
                                        .withExperimentalJpegXl()))) {
            encoded.drawOn(document, document.addNewPage(new PageSize(160, 120)), 0, 0, 160, 120);
        }
        var input = directory.resolve("combined.pdf");
        var normalized = directory.resolve("normalized.pdf");
        Files.write(input, output.toByteArray());
        var process =
                new ProcessBuilder(
                                "python3",
                                "scripts/verify-jxl.py",
                                input.toString(),
                                normalized.toString())
                        .redirectErrorStream(true)
                        .start();
        var log = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(0, process.waitFor(), log);
        try (var actual = org.apache.pdfbox.Loader.loadPDF(normalized.toFile());
                var expected = org.apache.pdfbox.Loader.loadPDF(pdf(source, false))) {
            var a = new org.apache.pdfbox.rendering.PDFRenderer(actual).renderImage(0);
            var b = new org.apache.pdfbox.rendering.PDFRenderer(expected).renderImage(0);
            assertArrayEquals(
                    b.getRGB(0, 0, 160, 120, null, 0, 160), a.getRGB(0, 0, 160, 120, null, 0, 160));
        }
    }

    @Test
    void qualityDistancesProduceValidatedDifferentBitstreams() {
        var source = pixels(256, 192);
        byte[] previous = new byte[0];
        for (var settings :
                new JpegXlOptions[] {
                    JpegXlOptions.lossless(),
                    JpegXlOptions.highQuality(),
                    JpegXlOptions.photos(),
                    JpegXlOptions.compact()
                }) {
            var encoded = NativeImages.jpegXlEncode(source, settings);
            var decoded = JpegXlCodecs.decode(encoded.samples());
            assertEquals(source.width(), decoded.width());
            assertEquals(source.height(), decoded.height());
            assertFalse(Arrays.equals(previous, encoded.samples()));
            previous = encoded.samples();
            if (settings.distance() == 0) assertArrayEquals(source.samples(), decoded.samples());
            try {
                Files.createDirectories(Path.of("build/jpeg-xl-pdfs"));
                Files.write(
                        Path.of("build/jpeg-xl-pdfs/distance-" + settings.distance() + ".pdf"),
                        pdf(encoded, true));
            } catch (java.io.IOException failure) {
                throw new java.io.UncheckedIOException(failure);
            }
        }
    }
}
