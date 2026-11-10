package org.skaldpdf.pdf;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BrotliUnavailableTest {
    @Test
    void keepsCoreUsableWithoutAnOptionalBrotliProvider() {
        assertTrue(Compression.BALANCED.available());
        assertFalse(Compression.brotliDecodingAvailable());
        assertFalse(Compression.BROTLI_BALANCED.available());
        var error = assertThrows(IllegalStateException.class, () -> {
            try (var document = new PdfDocument(new PdfWriter(new ByteArrayOutputStream(),
                new WriterProperties(Compression.BROTLI_BALANCED)))) {
                document.addNewPage(org.skaldpdf.geom.PageSize.A5);
            }
        });
        assertTrue(error.getMessage().contains("skald-brotli"));
    }
}
