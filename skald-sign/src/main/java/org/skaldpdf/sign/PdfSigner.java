package org.skaldpdf.sign;

import org.skaldpdf.pdf.IncrementalUpdate;
import org.skaldpdf.pdf.PdfDocument;
import org.skaldpdf.pdf.PdfReader;
import org.skaldpdf.pdf.PdfWriter;
import org.skaldpdf.pdf.SignatureField;
import org.skaldpdf.sign.internal.CmsVerifier;
import org.skaldpdf.sign.internal.SignaturePlaceholder;

import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Seals a PDF with a detached CMS signature and verifies the result.
 *
 * <p>This is an advanced electronic signature (AdES) integrity seal. It is not a
 * qualified electronic signature, ReAI/Skald is not a QTSP, and the output does
 * not by itself satisfy eIDAS QES requirements.
 */
public final class PdfSigner {
    private PdfSigner() {
    }

    public static byte[] sign(byte[] pdf, SigningKey key) {
        return sign(pdf, key, SignatureField.invisible("Signature1"), null);
    }

    public static byte[] sign(byte[] pdf, SigningKey key, SignatureField field) {
        return sign(pdf, key, field, null);
    }

    /**
     * Rewrites {@code pdf} so it contains {@code field}, then patches
     * {@code /ByteRange} and {@code /Contents} with a detached CMS signature.
     *
     * @param signingTime included in signed attributes when non-null; omit for
     *                    deterministic test output
     */
    public static byte[] sign(byte[] pdf, SigningKey key, SignatureField field, Instant signingTime) {
        Objects.requireNonNull(pdf, "pdf");
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(field, "field");
        var prepared = IncrementalUpdate.isSealed(pdf)
            ? IncrementalUpdate.appendSignaturePlaceholder(pdf, field)
            : reserve(pdf, field);
        return sealPrepared(prepared, key, signingTime);
    }

    /** Patches a document that already contains an unsigned signature placeholder. */
    public static byte[] sealPrepared(byte[] pdf, SigningKey key) {
        return sealPrepared(pdf, key, null);
    }

    public static byte[] sealPrepared(byte[] pdf, SigningKey key, Instant signingTime) {
        Objects.requireNonNull(pdf, "pdf");
        Objects.requireNonNull(key, "key");
        return SignaturePlaceholder.findUnsigned(pdf).seal(pdf, key, signingTime);
    }

    public static List<SignatureVerification> verify(byte[] pdf) {
        Objects.requireNonNull(pdf, "pdf");
        var result = new ArrayList<SignatureVerification>();
        for (var placeholder : SignaturePlaceholder.findAll(pdf)) {
            result.add(CmsVerifier.verify(
                placeholder.contents(pdf),
                placeholder.digest(pdf),
                placeholder.fieldName(),
                placeholder.reason(),
                placeholder.location(),
                placeholder.contact(),
                placeholder.pdfDate(),
                placeholder.subFilter()
            ));
        }
        return List.copyOf(result);
    }

    public static SignatureVerification verifySingle(byte[] pdf) {
        var signatures = verify(pdf);
        if (signatures.size() != 1) {
            throw new IllegalArgumentException("Expected one signature, found " + signatures.size());
        }
        return signatures.getFirst();
    }

    private static byte[] reserve(byte[] pdf, SignatureField field) {
        var output = new ByteArrayOutputStream();
        try (var document = new PdfDocument(new PdfReader(pdf), new PdfWriter(output))) {
            document.prepareSignature(field);
        }
        return output.toByteArray();
    }
}
