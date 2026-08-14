package org.skaldpdf.pdf;

import org.apache.pdfbox.Loader;
import org.skaldpdf.geom.PageSize;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PdfEncryptionTest {
    @Test
    void encryptsAPayslipSoPdfBoxOpensOnlyWithThePassword() throws Exception {
        var output = new ByteArrayOutputStream();
        var properties = WriterProperties.defaults().encrypted(PdfEncryption.userPassword("pin-4821"));
        try (var pdf = new PdfDocument(new PdfWriter(output, properties))) {
            pdf.addNewPage(PageSize.A5).append("BT /F1 12 Tf 72 400 Td (Salary) Tj ET\n");
        }
        var bytes = output.toByteArray();
        assertTrue(new String(bytes, 0, 8, java.nio.charset.StandardCharsets.US_ASCII).startsWith("%PDF-2.0"));
        var ascii = new String(bytes, java.nio.charset.StandardCharsets.ISO_8859_1);
        assertTrue(ascii.contains("/R 6"));
        assertTrue(ascii.contains("/AESV3"));

        assertThrows(Exception.class, () -> Loader.loadPDF(bytes));
        try (var opened = Loader.loadPDF(bytes, "pin-4821")) {
            assertEquals(1, opened.getNumberOfPages());
        }
    }

    @Test
    void rejectsAnEmptyPassword() {
        assertThrows(IllegalArgumentException.class, () -> PdfEncryption.userPassword("  "));
    }
}
