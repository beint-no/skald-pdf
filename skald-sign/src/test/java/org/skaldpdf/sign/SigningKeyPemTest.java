package org.skaldpdf.sign;

import org.junit.jupiter.api.Test;
import org.skaldpdf.pdf.SignatureField;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SigningKeyPemTest {
    @Test
    void pemRoundTripKeepsAValidSeal() {
        var original = SigningKey.selfSigned("Nordlys Handel AS");
        var restored = SigningKey.fromPem(original.privateKeyPem(), original.certificatePem());
        assertEquals(original.certificate().getSubjectX500Principal(),
            restored.certificate().getSubjectX500Principal());
        assertEquals(original.privateKey().getAlgorithm(), restored.privateKey().getAlgorithm());

        var output = new java.io.ByteArrayOutputStream();
        try (var document = new org.skaldpdf.pdf.PdfDocument(new org.skaldpdf.pdf.PdfWriter(output))) {
            document.addNewPage(org.skaldpdf.geom.PageSize.A5).append("% pem\n");
        }
        var signed = PdfSigner.sign(output.toByteArray(), restored, SignatureField.invisible("PemSeal")
            .withReason("PEM round-trip"));
        var verification = PdfSigner.verifySingle(signed);
        assertTrue(verification.valid(), String.join("; ", verification.notes()));
        assertTrue(original.privateKeyPem().contains("BEGIN PRIVATE KEY"));
        assertTrue(original.certificatePem().contains("BEGIN CERTIFICATE"));
        assertTrue(new String(signed, 0, 8, StandardCharsets.US_ASCII).startsWith("%PDF-2.0"));
    }
}
