package org.skaldpdf.pdf;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayOutputStream;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InvoiceCodecFixtureTest {
    private static final String LOGO_SHA256 =
        "c7dd2e8943b3727e303c3f54907db88033ec9b7120eec48c399571401a2e4b72";

    @Test
    void decodesApplicationLogoWithPngPredictors() throws Exception {
        var input = fixture("reai-invoice-flate.pdf");
        byte[] samples;
        try (var document = new PdfDocument(new PdfReader(input))) {
            assertEquals(1, document.importedImages().size());
            var image = document.importedImages().getFirst();
            assertTrue(image.safeToRecompress());
            var decoded = image.decode().orElseThrow();
            assertEquals(320, decoded.width());
            assertEquals(100, decoded.height());
            assertEquals(3, decoded.components());
            samples = decoded.samples();
        }
        assertEquals(96_000, samples.length);
        assertEquals(LOGO_SHA256, HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(samples)));
        try (var independent = Loader.loadPDF(input)) {
            var resources = independent.getPage(0).getResources();
            var stream = resources.getXObject(resources.getXObjectNames().iterator().next()).getCOSObject();
            try (var decoded = stream.createInputStream()) {
                assertArrayEquals(samples, decoded.readAllBytes());
            }
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {2, 15})
    void preservesUnsupportedBrotliImageAndItsPredictorOnRewrite(int predictor) throws Exception {
        var input = fixture("reai-invoice-brotli-predictor-" + predictor + ".pdf");
        var output = new ByteArrayOutputStream();
        byte[] encoded;
        try (var document = new PdfDocument(new PdfReader(input), new PdfWriter(output))) {
            assertEquals(1, document.importedImages().size());
            var image = document.importedImages().getFirst();
            assertEquals(320, image.width());
            assertEquals(100, image.height());
            assertEquals(8, image.bitsPerComponent());
            assertTrue(image.filter().contains("BrotliDecode"));
            assertFalse(image.safeToRecompress());
            assertTrue(image.decode().isEmpty(), "Unsupported decoding must not return predicted bytes as pixels");
            encoded = image.encodedBytes();
        }
        var rewritten = output.toByteArray();
        var text = PdfText.extract(input);
        assertFalse(text.isBlank());
        assertEquals(PdfText.extract(fixture("reai-invoice-flate.pdf")), text);
        assertEquals(text, PdfText.extract(rewritten));
        for (var pdf : new byte[][]{input, rewritten}) {
            try (var independent = Loader.loadPDF(pdf)) {
                assertEquals(1, independent.getNumberOfPages());
                var resources = independent.getPage(0).getResources();
                var stream = resources.getXObject(resources.getXObjectNames().iterator().next()).getCOSObject();
                assertEquals(COSName.getPDFName("BrotliDecode"), stream.getDictionaryObject(COSName.FILTER));
                var parameters = (COSDictionary) stream.getDictionaryObject(COSName.DECODE_PARMS);
                assertEquals(predictor, parameters.getInt(COSName.PREDICTOR));
                assertEquals(320, parameters.getInt(COSName.COLUMNS));
                assertEquals(3, parameters.getInt(COSName.COLORS));
                assertEquals(8, parameters.getInt(COSName.BITS_PER_COMPONENT));
                try (var raw = stream.createRawInputStream()) {
                    assertArrayEquals(encoded, raw.readAllBytes());
                }
                var extensions = (COSDictionary) independent.getDocumentCatalog().getCOSObject()
                    .getDictionaryObject(COSName.getPDFName("Extensions"));
                var brotli = (COSDictionary) extensions.getDictionaryObject(COSName.getPDFName("PDFa"));
                assertEquals("2.0", brotli.getNameAsString(COSName.getPDFName("BaseVersion")));
                assertEquals(1, brotli.getInt(COSName.getPDFName("ExtensionLevel")));
            }
        }
    }

    private static byte[] fixture(String name) throws Exception {
        try (var input = Objects.requireNonNull(InvoiceCodecFixtureTest.class.getResourceAsStream(
            "/codec/reai-invoice/" + name))) {
            return input.readAllBytes();
        }
    }
}
