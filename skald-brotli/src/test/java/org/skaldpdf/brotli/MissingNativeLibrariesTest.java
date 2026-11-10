package org.skaldpdf.brotli;

import org.junit.jupiter.api.Test;
import org.skaldpdf.geom.PageSize;
import org.skaldpdf.pdf.Compression;
import org.skaldpdf.pdf.PdfDocument;
import org.skaldpdf.pdf.PdfReader;
import org.skaldpdf.pdf.PdfWriter;
import org.skaldpdf.pdf.WriterProperties;

import java.io.ByteArrayOutputStream;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MissingNativeLibrariesTest {
    @Test
    void servicePresenceDoesNotMakeNativeLibrariesMandatoryForConventionalPdfs() throws Exception {
        assertFalse(Compression.brotliDecodingAvailable());
        assertFalse(Compression.BROTLI_BALANCED.available());
        var output = new ByteArrayOutputStream();
        try (var document = new PdfDocument(new PdfWriter(output))) {
            document.addNewPage(PageSize.A5);
        }
        try (var document = new PdfDocument(new PdfReader(output.toByteArray()))) {
            assertEquals(1, document.getNumberOfPages());
        }
        try (var input = Objects.requireNonNull(getClass().getResourceAsStream(
            "/codec/reai-invoice/reai-invoice-brotli-predictor-15.pdf"));
             var document = new PdfDocument(new PdfReader(input.readAllBytes()))) {
            var image = document.importedImages().getFirst();
            assertTrue(image.decode().isEmpty());
            assertFalse(image.safeToRecompress());
        }
        assertThrows(IllegalStateException.class, () -> {
            try (var document = new PdfDocument(new PdfWriter(new ByteArrayOutputStream(),
                new WriterProperties(Compression.BROTLI_BALANCED)))) {
                document.addNewPage(PageSize.A5);
            }
        });
    }
}
