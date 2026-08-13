package org.skaldpdf.sign;

import java.util.List;
import java.util.Objects;

/**
 * Result of verifying one PDF signature. {@link #valid()} is true only when the
 * ByteRange digest matches and the CMS signature verifies with the embedded
 * certificate. This is an integrity check, not a qualified-status check.
 */
public record SignatureVerification(
    boolean byteRangeIntact,
    boolean cmsSignatureValid,
    boolean valid,
    String fieldName,
    String reason,
    String location,
    String contact,
    String pdfDate,
    String subFilter,
    String profile,
    String subject,
    String issuer,
    String serialHex,
    String digestAlgorithm,
    List<String> notes
) {
    public SignatureVerification {
        notes = List.copyOf(Objects.requireNonNull(notes, "notes"));
    }
}
