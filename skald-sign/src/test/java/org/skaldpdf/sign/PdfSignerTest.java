package org.skaldpdf.sign;

import org.apache.pdfbox.Loader;
import org.skaldpdf.geom.PageSize;
import org.skaldpdf.geom.Rectangle;
import org.skaldpdf.pdf.PdfDocument;
import org.skaldpdf.pdf.PdfWriter;
import org.skaldpdf.pdf.SignatureField;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PdfSignerTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void sealsAndVerifiesAPreparedDocument() throws Exception {
        var key = SigningKey.selfSigned("Nordlys Handel AS");
        var prepared = unsignedInvoice(SignatureField.invisible("InvoiceSeal")
            .withReason("Issued invoice")
            .withLocation("Oslo, Norway")
            .withContact("faktura@nordlys.example")
            .withPdfDate("D:20260813120000Z"));
        var signed = PdfSigner.sealPrepared(prepared, key, Instant.parse("2026-08-13T12:00:00Z"));

        assertTrue(new String(signed, 0, 8, StandardCharsets.US_ASCII).startsWith("%PDF-2.0"));
        var verification = PdfSigner.verifySingle(signed);
        assertTrue(verification.byteRangeIntact(), String.join("; ", verification.notes()));
        assertTrue(verification.cmsSignatureValid(), String.join("; ", verification.notes()));
        assertTrue(verification.valid());
        assertEquals("InvoiceSeal", verification.fieldName());
        assertEquals("Issued invoice", verification.reason());
        assertEquals("Oslo, Norway", verification.location());
        assertTrue(verification.subject().contains("Nordlys Handel AS"));
        assertTrue(verification.profile().contains("PAdES-B-B"));

        try (var parsed = Loader.loadPDF(signed)) {
            assertEquals(1, parsed.getSignatureDictionaries().size());
            var signature = parsed.getSignatureDictionaries().getFirst();
            assertEquals("adbe.pkcs7.detached", signature.getSubFilter());
            assertEquals("Issued invoice", signature.getReason());
        }
    }

    @Test
    void rewriteSignDetectsTampering() {
        var key = SigningKey.selfSigned("Auditor");
        var unsigned = unsignedInvoice(null);
        var signed = PdfSigner.sign(unsigned, key, SignatureField.invisible("Audit")
            .withReason("Books closed"));
        assertTrue(PdfSigner.verifySingle(signed).valid());

        var tampered = signed.clone();
        tampered[1] ^= 0x01;
        var verification = PdfSigner.verifySingle(tampered);
        assertFalse(verification.byteRangeIntact());
        assertFalse(verification.valid());
    }

    @Test
    void roundTripsAPkcs12AndSupportsPadesSubFilter() throws Exception {
        var original = SigningKey.selfSigned("ReAI Books AS");
        var store = temporaryDirectory.resolve("signing.p12");
        var password = "test-only".toCharArray();
        original.storePkcs12(store, password);
        var loaded = SigningKey.fromPkcs12(store, password);

        var signed = PdfSigner.sign(unsignedInvoice(null), loaded,
            SignatureField.invisible("QualifiedLookingButNot")
                .withSubFilter(SignatureField.PADES_B_B)
                .withReason("Accounting seal"));
        var verification = PdfSigner.verifySingle(signed);
        assertTrue(verification.valid(), String.join("; ", verification.notes()));
        assertEquals("ETSI.CAdES.detached", verification.subFilter());
        assertEquals("PAdES-B-B", verification.profile());

        try (var parsed = Loader.loadPDF(signed)) {
            assertEquals("ETSI.CAdES.detached", parsed.getSignatureDictionaries().getFirst().getSubFilter());
        }
    }

    @Test
    void visibleFieldKeepsAWidgetAppearance() throws Exception {
        var key = SigningKey.selfSigned("Visible Signer");
        var field = new SignatureField("Visible", 1, new Rectangle(72, 80, 160, 40),
            "Reviewed", "Oslo", null, "D:20260813120000Z",
            SignatureField.DEFAULT_RESERVED_BYTES, SignatureField.ADOBE_PKCS7);
        var signed = PdfSigner.sign(unsignedInvoice(null), key, field);
        assertTrue(PdfSigner.verifySingle(signed).valid());
        var ascii = new String(signed, StandardCharsets.ISO_8859_1);
        assertTrue(ascii.contains("/Subtype /Form"));
        try (var parsed = Loader.loadPDF(signed)) {
            assertEquals(1, parsed.getNumberOfPages());
        }
    }

    private static byte[] unsignedInvoice(SignatureField field) {
        var output = new ByteArrayOutputStream();
        try (var document = new PdfDocument(new PdfWriter(output))) {
            document.addNewPage(PageSize.A4).append("% Invoice 1001\n");
            if (field != null) {
                document.prepareSignature(field);
            }
        }
        return output.toByteArray();
    }

}
