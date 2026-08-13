package org.skaldpdf.pdf;

import org.skaldpdf.geom.PageSize;
import org.skaldpdf.geom.Rectangle;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SignatureReservationTest {
    @Test
    void writesAnUnpackedSignaturePlaceholder() {
        var output = new ByteArrayOutputStream();
        try (var document = new PdfDocument(new PdfWriter(output))) {
            document.addNewPage(PageSize.A4).append("% Invoice 1001\n");
            document.prepareSignature(SignatureField.invisible("InvoiceSeal")
                .withReason("Issued invoice")
                .withLocation("Oslo")
                .withPdfDate("D:20260813120000Z"));
        }
        var ascii = output.toString(StandardCharsets.ISO_8859_1);
        assertTrue(ascii.startsWith("%PDF-2.0"));
        assertTrue(ascii.contains("/Type /Sig"));
        assertTrue(ascii.contains("/Filter /Adobe.PPKLite"));
        assertTrue(ascii.contains("/SubFilter /adbe.pkcs7.detached"));
        assertTrue(ascii.contains("/ByteRange [0 0000000000 0000000000 0000000000]"));
        assertTrue(ascii.contains("/Contents <"));
        assertTrue(ascii.contains("/Reason (Issued invoice)"));
        assertTrue(ascii.contains("/Location (Oslo)"));
        assertTrue(ascii.contains("/AcroForm"));
        assertTrue(ascii.contains("/SigFlags 3"));
        assertTrue(ascii.contains("/FT /Sig"));
        assertTrue(ascii.contains("/T (InvoiceSeal)"));
        assertFalse(ascii.contains("/DA (/Helv"), "PDF 2.0 output must not reference unembedded Helvetica");
    }

    @Test
    void reservesAVisibleWidgetAppearance() {
        var output = new ByteArrayOutputStream();
        try (var document = new PdfDocument(new PdfWriter(output))) {
            document.addNewPage(PageSize.A4);
            document.prepareSignature(new SignatureField(
                "VisibleSeal", 1, new Rectangle(72, 72, 180, 48),
                "Reviewed", null, null, null,
                SignatureField.DEFAULT_RESERVED_BYTES, SignatureField.PADES_B_B));
        }
        var ascii = output.toString(StandardCharsets.ISO_8859_1);
        assertTrue(ascii.contains("/SubFilter /ETSI.CAdES.detached"));
        assertTrue(ascii.contains("/Subtype /Form"));
        assertTrue(ascii.contains("/AP << /N "));
    }
}
