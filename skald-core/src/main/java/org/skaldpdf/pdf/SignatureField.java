package org.skaldpdf.pdf;

import org.skaldpdf.geom.Rectangle;

import java.util.Objects;

/**
 * Structural signature field emitted by the writer. Cryptographic sealing lives
 * in the optional {@code skald-sign} module; core only reserves the PDF objects.
 */
public record SignatureField(
    String fieldName,
    int pageNumber,
    Rectangle rect,
    String reason,
    String location,
    String contact,
    String pdfDate,
    int reservedContentBytes,
    String subFilter
) {
    public static final int DEFAULT_RESERVED_BYTES = 8192;
    /** Widest viewer support (Acrobat, Preview). CMS still carries PAdES-B-B attributes. */
    public static final String ADOBE_PKCS7 = "adbe.pkcs7.detached";
    /** Strict PAdES-B-B SubFilter. Use when a validator requires ETSI.CAdES.detached. */
    public static final String PADES_B_B = "ETSI.CAdES.detached";

    public SignatureField {
        Objects.requireNonNull(fieldName, "fieldName");
        reason = reason == null ? "" : reason;
        location = location == null ? "" : location;
        contact = contact == null ? "" : contact;
        pdfDate = pdfDate == null ? "" : pdfDate;
        if (fieldName.isBlank() || fieldName.codePoints().anyMatch(code -> code < 0x20 || code > 0x7e)) {
            throw new IllegalArgumentException("Signature field names must be printable ASCII");
        }
        if (pageNumber < 1) {
            throw new IllegalArgumentException("Signature page numbers are one-based");
        }
        Objects.requireNonNull(rect, "rect");
        if (reservedContentBytes < 1024 || reservedContentBytes > 65_536) {
            throw new IllegalArgumentException("Reserved CMS size must be between 1 KiB and 64 KiB");
        }
        Objects.requireNonNull(subFilter, "subFilter");
        if (!ADOBE_PKCS7.equals(subFilter) && !PADES_B_B.equals(subFilter)) {
            throw new IllegalArgumentException("Unsupported signature SubFilter: " + subFilter);
        }
    }

    public static SignatureField invisible(String fieldName) {
        return new SignatureField(fieldName, 1, new Rectangle(0, 0, 0, 0),
            "", "", "", "", DEFAULT_RESERVED_BYTES, ADOBE_PKCS7);
    }

    public SignatureField onPage(int page) {
        return new SignatureField(fieldName, page, rect, reason, location, contact, pdfDate,
            reservedContentBytes, subFilter);
    }

    public SignatureField withReason(String value) {
        return new SignatureField(fieldName, pageNumber, rect, value, location, contact, pdfDate,
            reservedContentBytes, subFilter);
    }

    public SignatureField withLocation(String value) {
        return new SignatureField(fieldName, pageNumber, rect, reason, value, contact, pdfDate,
            reservedContentBytes, subFilter);
    }

    public SignatureField withContact(String value) {
        return new SignatureField(fieldName, pageNumber, rect, reason, location, value, pdfDate,
            reservedContentBytes, subFilter);
    }

    public SignatureField withPdfDate(String value) {
        return new SignatureField(fieldName, pageNumber, rect, reason, location, contact, value,
            reservedContentBytes, subFilter);
    }

    public SignatureField withReservedContentBytes(int value) {
        return new SignatureField(fieldName, pageNumber, rect, reason, location, contact, pdfDate,
            value, subFilter);
    }

    public SignatureField withSubFilter(String value) {
        return new SignatureField(fieldName, pageNumber, rect, reason, location, contact, pdfDate,
            reservedContentBytes, value);
    }

    public boolean visible() {
        return rect.getWidth() > 0 && rect.getHeight() > 0;
    }
}
