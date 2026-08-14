package org.skaldpdf.pdf;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IncrementalUpdateTest {
    @Test
    void allowsRewriteOfUnsignedFiles() {
        var unsigned = unsignedPage();
        assertFalse(IncrementalUpdate.isSealed(unsigned));
        try (var document = new PdfDocument(new PdfReader(unsigned), new PdfWriter(new ByteArrayOutputStream()))) {
            assertTrue(document.getNumberOfPages() == 1);
        }
    }

    @Test
    void appendsASecondPlaceholderAfterASealedRevision() {
        var first = IncrementalUpdate.appendSignaturePlaceholder(unsignedPage(),
            SignatureField.invisible("First"));
        assertTrue(new String(first, java.nio.charset.StandardCharsets.ISO_8859_1)
            .contains("/ByteRange [0 0000000000 0000000000 0000000000]"));
        var second = IncrementalUpdate.appendSignaturePlaceholder(first,
            SignatureField.invisible("Second"));
        var ascii = new String(second, java.nio.charset.StandardCharsets.ISO_8859_1);
        assertTrue(ascii.contains("/T (First)") || ascii.contains("/T (Second)"));
        assertTrue(ascii.contains("startxref"));
    }

    private static byte[] unsignedPage() {
        var output = new ByteArrayOutputStream();
        try (var pdf = new PdfDocument(new PdfWriter(output))) {
            pdf.addNewPage(org.skaldpdf.geom.PageSize.A5).append("% page\n");
        }
        return output.toByteArray();
    }
}
