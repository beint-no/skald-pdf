package org.skaldpdf.brotli;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.skaldpdf.fonts.SkaldSans;
import org.skaldpdf.geom.PageSize;
import org.skaldpdf.image.ImageData;
import org.skaldpdf.pdf.CanonicalRewriteConstraint;
import org.skaldpdf.pdf.Compression;
import org.skaldpdf.pdf.PdfDocument;
import org.skaldpdf.pdf.PdfReader;
import org.skaldpdf.pdf.PdfText;
import org.skaldpdf.pdf.PdfWriter;
import org.skaldpdf.pdf.WriterProperties;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BrotliPdfTest {
    private static final byte[] RGB = { -1, 0, 0, 0, -1, 0, 0, 0, -1, -1, -1, 0, 0, -1, -1, -1, 0, -1 };

    @BeforeAll
    static void requireCodec() {
        NativeBrotliCodecTest.requireNativeCodecInCi();
    }

    @ParameterizedTest
    @ValueSource(strings = {"flate", "brotli-predictor-2", "brotli-predictor-15"})
    void readsAndRewritesApplicationInvoices(String encoding) throws Exception {
        var input = resource("/codec/reai-invoice/reai-invoice-" + encoding + ".pdf");
        var originalText = PdfText.extract(input);
        assertFalse(originalText.isBlank());
        for (var compression : new Compression[]{Compression.BALANCED, Compression.BROTLI_BALANCED}) {
            var output = new ByteArrayOutputStream();
            try (var document = new PdfDocument(new PdfReader(input), new PdfWriter(output, new WriterProperties(compression)))) {
                var image = document.importedImages().getFirst();
                assertTrue(image.safeToRecompress());
                var samples = image.decode().orElseThrow().samples();
                assertEquals("c7dd2e8943b3727e303c3f54907db88033ec9b7120eec48c399571401a2e4b72",
                    HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(samples)));
                document.compressImportedStreamsLosslessly();
            }
            assertEquals(originalText, PdfText.extract(output.toByteArray()));
            try (var document = new PdfDocument(new PdfReader(output.toByteArray()))) {
                assertTrue(document.importedImages().getFirst().decode().isPresent());
            }
        }
    }

    @ParameterizedTest
    @EnumSource(value = Compression.class)
    void writesAndReadsImagesTextFontsAndObjectStreams(Compression compression) throws Exception {
        var bytes = generated(compression);
        try (var document = new PdfDocument(new PdfReader(bytes))) {
            assertEquals(1, document.getNumberOfPages());
            var image = document.importedImages().getFirst();
            assertArrayEquals(RGB, image.decode().orElseThrow().samples());
            assertTrue(image.requiresOriginalDimensions());
            assertArrayEquals(new byte[]{0, 64, -128, -64, -1, 32},
                image.withResampledSoftMask().orElseThrow().decodeSoftMask().orElseThrow().samples());
        }
        assertEquals(PdfText.extract(generated(Compression.BALANCED)), PdfText.extract(bytes));
        var syntax = new String(bytes, StandardCharsets.ISO_8859_1);
        assertEquals(compression.name().startsWith("BROTLI"), syntax.contains("/BrotliDecode"));
        if (compression.name().startsWith("BROTLI")) {
            assertTrue(syntax.contains("/ExtensionRevision (2026)"));
            assertTrue(syntax.contains("/BaseVersion /2.0"));
        }
        Files.createDirectories(Path.of("build/brotli-pdfs"));
        Files.write(Path.of("build/brotli-pdfs/" + compression.name().toLowerCase(java.util.Locale.ROOT) + ".pdf"), bytes);
    }

    @ParameterizedTest
    @EnumSource(value = Compression.class, names = {"BROTLI_FAST", "BROTLI_BALANCED", "BROTLI_MAXIMUM"})
    void independentReferenceDecoderAndPdfBoxRenderMatchFlate(Compression compression, @TempDir Path directory) throws Exception {
        requireReferenceTools();
        var input = directory.resolve("input.pdf");
        var normalized = directory.resolve("normalized.pdf");
        Files.write(input, generated(compression));
        var verifier = new ProcessBuilder("python3", "scripts/verify-brotli.py", input.toString(), normalized.toString())
            .redirectErrorStream(true).start();
        var log = new String(verifier.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(0, verifier.waitFor(), log);
        try (var actual = Loader.loadPDF(normalized.toFile()); var control = Loader.loadPDF(generated(Compression.BALANCED))) {
            var rendered = new PDFRenderer(actual).renderImage(0, 1, ImageType.RGB);
            var expected = new PDFRenderer(control).renderImage(0, 1, ImageType.RGB);
            assertArrayEquals(expected.getRGB(0, 0, 120, 80, null, 0, 120),
                rendered.getRGB(0, 0, 120, 80, null, 0, 120));
            var extensions = actual.getDocumentCatalog().getCOSObject().getCOSDictionary(
                org.apache.pdfbox.cos.COSName.getPDFName("Extensions"));
            var declaration = extensions.getCOSDictionary(org.apache.pdfbox.cos.COSName.getPDFName("PDFa"));
            assertEquals("2026", declaration.getString(org.apache.pdfbox.cos.COSName.getPDFName("ExtensionRevision")));
            assertEquals("https://pdfa.org/resource/extension-brotli",
                declaration.getString(org.apache.pdfbox.cos.COSName.getPDFName("URL")));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"standard", "large-window"})
    void readsIndependentBrotliXrefAndObjectStreams(String name) throws Exception {
        var input = resource("/brotli/structure-" + name + ".pdf");
        var output = new ByteArrayOutputStream();
        try (var document = new PdfDocument(new PdfReader(input),
            new PdfWriter(output, new WriterProperties(Compression.BROTLI_BALANCED)))) {
            assertEquals(1, document.getNumberOfPages());
            assertArrayEquals(RGB, document.importedImages().getFirst().decode().orElseThrow().samples());
        }
        try (var document = new PdfDocument(new PdfReader(output.toByteArray()))) {
            assertArrayEquals(RGB, document.importedImages().getFirst().decode().orElseThrow().samples());
        }
    }

    @Test
    void upgradesLegacyPdfAndRetainsOtherExtensionDeclarations(@TempDir Path directory) throws Exception {
        requireReferenceTools();
        var encoded = new NativeBrotliCodec().encode(RGB, 6);
        var input = imagePdf(encoded, "/BrotliDecode", "",
            "/Version /1.7 /Extensions << /ISO_ << /BaseVersion /2.0 /ExtensionLevel 32002 >> "
                + "/PDFa << /BaseVersion /2.0 /ExtensionLevel 42 >> >>");
        input[5] = '1'; input[7] = '7';
        var output = new ByteArrayOutputStream();
        try (var document = new PdfDocument(new PdfReader(input),
            new PdfWriter(output, new WriterProperties(Compression.BROTLI_BALANCED)))) {
            assertEquals(1, document.getNumberOfPages());
        }
        var syntax = new String(output.toByteArray(), StandardCharsets.ISO_8859_1);
        assertTrue(syntax.startsWith("%PDF-2.0"));
        var file = directory.resolve("legacy.pdf");
        var normalized = directory.resolve("legacy-flate.pdf");
        Files.write(file, output.toByteArray());
        var process = new ProcessBuilder("python3", "scripts/verify-brotli.py", file.toString(), normalized.toString())
            .redirectErrorStream(true).start();
        var log = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(0, process.waitFor(), log);
        try (var independent = Loader.loadPDF(normalized.toFile())) {
            var extensions = independent.getDocumentCatalog().getCOSObject().getCOSDictionary(
                org.apache.pdfbox.cos.COSName.getPDFName("Extensions"));
            var iso = extensions.getCOSDictionary(org.apache.pdfbox.cos.COSName.getPDFName("ISO_"));
            assertEquals(32002, iso.getInt(org.apache.pdfbox.cos.COSName.getPDFName("ExtensionLevel")));
            var pdfa = extensions.getCOSArray(org.apache.pdfbox.cos.COSName.getPDFName("PDFa"));
            assertEquals(2, pdfa.size());
            assertEquals(42, ((org.apache.pdfbox.cos.COSDictionary) pdfa.getObject(0))
                .getInt(org.apache.pdfbox.cos.COSName.getPDFName("ExtensionLevel")));
            assertEquals(1, ((org.apache.pdfbox.cos.COSDictionary) pdfa.getObject(1))
                .getInt(org.apache.pdfbox.cos.COSName.getPDFName("ExtensionLevel")));
        }
    }

    @Test
    void detectsTruncatedAndTrailingReachableBrotliStreams() {
        var codec = new NativeBrotliCodec();
        var original = codec.encode(RGB, 6);
        for (var encoded : new byte[][]{
            java.util.Arrays.copyOf(original, original.length - 1),
            java.util.Arrays.copyOf(original, original.length + 1)}) {
            try (var document = new PdfDocument(new PdfReader(imagePdf(encoded, "/BrotliDecode", "")))) {
                assertTrue(document.importedImages().getFirst().decode().isEmpty());
                assertTrue(document.canonicalRewriteConstraints().contains(CanonicalRewriteConstraint.MALFORMED_STREAM));
            }
        }
    }

    @Test
    void appliesDecodeParametersToTheCorrespondingFilter() {
        // TIFF-predicted RGB data, then Brotli, then ASCIIHex. The null DecodeParms
        // belongs to ASCIIHex; using the first entry for Brotli returns incorrect pixels.
        var predicted = RGB.clone();
        for (int row = 0; row < 2; row++) {
            for (int i = 8; i >= 3; i--) {
                predicted[row * 9 + i] = (byte) (RGB[row * 9 + i] - RGB[row * 9 + i - 3]);
            }
        }
        var hex = HexFormat.of().formatHex(new NativeBrotliCodec().encode(predicted, 6)) + ">";
        var parameters = "/DecodeParms [null << /Predictor 2 /Colors 3 /Columns 3 /BitsPerComponent 8 >>]";
        try (var document = new PdfDocument(new PdfReader(imagePdf(hex.getBytes(StandardCharsets.US_ASCII),
            "[/ASCIIHexDecode /BrotliDecode]", parameters)))) {
            assertArrayEquals(RGB, document.importedImages().getFirst().decode().orElseThrow().samples());
        }
        var invalid = parameters.replace("[null ", "[");
        try (var document = new PdfDocument(new PdfReader(imagePdf(hex.getBytes(StandardCharsets.US_ASCII),
            "[/ASCIIHexDecode /BrotliDecode]", invalid)))) {
            assertTrue(document.importedImages().getFirst().decode().isEmpty());
        }
    }

    @Test
    void resamplesAndRewritesABrotliSoftMask() {
        for (var compression : new Compression[]{Compression.BALANCED, Compression.BROTLI_BALANCED}) {
            var output = new ByteArrayOutputStream();
            try (var document = new PdfDocument(new PdfReader(generated(Compression.BROTLI_BALANCED)),
                new PdfWriter(output, new WriterProperties(compression)))) {
                var image = document.importedImages().getFirst().withResampledSoftMask().orElseThrow();
                var jpeg = org.skaldpdf.codec.RasterImages.asJpeg(ImageData.fromRgb(1, 1, new byte[]{10, 20, 30}), 0.9f);
                document.replaceImportedImage(image, jpeg, ImageData.fromGray(1, 1, new byte[]{-128}));
            }
            try (var document = new PdfDocument(new PdfReader(output.toByteArray()))) {
                var image = document.importedImages().getFirst();
                assertEquals(1, image.width());
                assertEquals(1, image.height());
                assertArrayEquals(new byte[]{-128}, image.withResampledSoftMask().orElseThrow()
                    .decodeSoftMask().orElseThrow().samples());
            }
        }
    }

    @Test
    void optimizerCanOptInWithoutChangingItsDefaults() throws Exception {
        assertEquals(Compression.MAXIMUM, org.skaldpdf.optimize.OptimizeOptions.attachments().streamCompression());
        var input = resource("/codec/reai-invoice/reai-invoice-flate.pdf");
        var options = org.skaldpdf.optimize.OptimizeOptions.builder()
            .streamCompression(Compression.BROTLI_BALANCED).convertLosslessRaster(false).recompressJpeg(false)
            .removeTrueTypeHinting(false).removeUnusedGlyphs(false).minimumSavingsBytes(1).minimumSavingsPercent(0).build();
        var result = org.skaldpdf.optimize.PdfOptimizer.recompress(input, options);
        assertTrue(result.length < input.length);
        assertTrue(new String(result, StandardCharsets.ISO_8859_1).contains("BrotliDecode"));
        assertEquals(PdfText.extract(input), PdfText.extract(result));
    }

    @Test
    void refusesToRetainAnUnvalidatedConformanceDeclaration() {
        var input = classicPdf(new byte[][]{
            ascii("<< /Type /Catalog /Pages 2 0 R /Metadata 4 0 R >>"),
            ascii("<< /Type /Pages /Count 1 /Kids [3 0 R] >>"),
            ascii("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 3 2] /Resources << >> >>"),
            stream("/Type /Metadata /Subtype /XML", ascii("<pdfaid:part>4</pdfaid:part>"))
        });
        var output = new ByteArrayOutputStream();
        assertThrows(IllegalArgumentException.class, () -> {
            try (var document = new PdfDocument(new PdfReader(input),
                new PdfWriter(output, new WriterProperties(Compression.BROTLI_BALANCED)))) {
                assertEquals(1, document.getNumberOfPages());
            }
        });
        assertEquals(0, output.size());
    }

    @Test
    void supportsEncryptionAfterBrotliCompression() throws Exception {
        var properties = new WriterProperties(Compression.BROTLI_BALANCED)
            .encrypted(org.skaldpdf.pdf.PdfEncryption.userPassword("brotli-test"));
        var bytes = generated(properties);
        var syntax = new String(bytes, StandardCharsets.ISO_8859_1);
        assertTrue(syntax.contains("/AESV3"));
        assertTrue(syntax.contains("/BrotliDecode"));
        assertFalse(syntax.contains("/ExtensionRevision (2026)"), "Extension strings must be encrypted too");
        Files.createDirectories(Path.of("build/brotli-pdfs"));
        Files.write(Path.of("build/brotli-pdfs/encrypted.pdf"), bytes);
    }

    private static byte[] generated(Compression compression) {
        return generated(new WriterProperties(compression));
    }

    private static byte[] generated(WriterProperties properties) {
        var output = new ByteArrayOutputStream();
        try (var document = new PdfDocument(new PdfWriter(output, properties))) {
            var page = document.addNewPage(new PageSize(120, 80));
            ImageData.fromRgb(3, 2, RGB, new byte[]{0, 64, -128, -64, -1, 32}).drawOn(document, page, 10, 10, 90, 40);
            var font = SkaldSans.regular();
            var run = font.glyphRun("Brotli PDF ready");
            var name = page.registerFont(font, run);
            var text = new StringBuilder("BT /").append(name).append(" 10 Tf 10 60 Td <");
            for (var glyph : run.glyphs()) {
                text.append(String.format(java.util.Locale.ROOT, "%04X", glyph));
            }
            page.append(text.append("> Tj ET\n").toString());
        }
        return output.toByteArray();
    }

    static byte[] imagePdf(byte[] image, String filter, String parameters) {
        return imagePdf(image, filter, parameters, "");
    }

    static byte[] imagePdf(byte[] image, String filter, String parameters, String catalogEntries) {
        var content = "q 3 0 0 2 0 0 cm /Im0 Do Q\n".getBytes(StandardCharsets.US_ASCII);
        var objects = new byte[][]{
            ascii("<< /Type /Catalog /Pages 2 0 R " + catalogEntries + " >>"),
            ascii("<< /Type /Pages /Count 1 /Kids [3 0 R] >>"),
            ascii("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 3 2] /Resources << /XObject << /Im0 4 0 R >> >> /Contents 5 0 R >>"),
            stream("/Type /XObject /Subtype /Image /Width 3 /Height 2 /ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter "
                + filter + " " + parameters, image),
            stream("", content)
        };
        return classicPdf(objects);
    }

    static byte[] classicPdf(byte[][] objects) {
        var output = new ByteArrayOutputStream();
        output.writeBytes(ascii("%PDF-2.0\n"));
        var offsets = new int[objects.length + 1];
        for (int i = 0; i < objects.length; i++) {
            offsets[i + 1] = output.size();
            output.writeBytes(ascii((i + 1) + " 0 obj\n"));
            output.writeBytes(objects[i]);
            output.writeBytes(ascii("\nendobj\n"));
        }
        var xref = output.size();
        output.writeBytes(ascii("xref\n0 " + offsets.length + "\n0000000000 65535 f \n"));
        for (int i = 1; i < offsets.length; i++) {
            output.writeBytes(ascii(String.format(java.util.Locale.ROOT, "%010d 00000 n \n", offsets[i])));
        }
        output.writeBytes(ascii("trailer\n<< /Size " + offsets.length + " /Root 1 0 R >>\nstartxref\n" + xref + "\n%%EOF\n"));
        return output.toByteArray();
    }

    static byte[] stream(String dictionary, byte[] bytes) {
        var output = new ByteArrayOutputStream();
        output.writeBytes(ascii("<< " + dictionary + " /Length " + bytes.length + " >>\nstream\n"));
        output.writeBytes(bytes);
        output.writeBytes(ascii("\nendstream"));
        return output.toByteArray();
    }

    static byte[] ascii(String value) {
        return value.getBytes(StandardCharsets.US_ASCII);
    }

    private static void requireReferenceTools() throws Exception {
        boolean tools = true;
        try {
            for (var command : new String[]{"brotli", "python3"}) {
                tools &= new ProcessBuilder(command, "--version").redirectErrorStream(true).start().waitFor() == 0;
            }
        } catch (java.io.IOException missing) {
            tools = false;
        }
        if ("1".equals(System.getenv("SKALD_REQUIRE_BROTLI_TESTS"))) {
            assertTrue(tools, "CI requires Python 3 and the reference brotli CLI");
        }
        org.junit.jupiter.api.Assumptions.assumeTrue(tools);
    }

    private static byte[] resource(String name) throws Exception {
        try (var input = Objects.requireNonNull(BrotliPdfTest.class.getResourceAsStream(name))) {
            return input.readAllBytes();
        }
    }
}
