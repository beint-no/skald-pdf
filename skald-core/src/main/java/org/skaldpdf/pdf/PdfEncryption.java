package org.skaldpdf.pdf;

import java.util.Objects;

/**
 * PDF 2.0 Standard Security Handler (revision 6, AES-256). Use this for
 * confidential generated files such as payslips. Recipients open the file with
 * the user password in Acrobat or Preview.
 *
 * <p>Encrypted files cannot be parsed or stamped by Skald, and cannot be
 * combined with a signature in the same write. Generate, then encrypt.
 */
public final class PdfEncryption {
    /** All owner-style permissions (print, copy, extract, assemble). */
    public static final int PERMIT_ALL = 0xFFFF_FFFC;

    private final String userPassword;
    private final String ownerPassword;
    private final int permissions;

    private PdfEncryption(String userPassword, String ownerPassword, int permissions) {
        this.userPassword = userPassword;
        this.ownerPassword = ownerPassword;
        this.permissions = permissions;
    }

    /**
     * Encrypts so the file opens with {@code userPassword}. The owner password
     * defaults to the same value (full permission).
     */
    public static PdfEncryption userPassword(String userPassword) {
        var normalized = normalizePassword(userPassword, "userPassword");
        return new PdfEncryption(normalized, normalized, PERMIT_ALL);
    }

    public PdfEncryption ownerPassword(String ownerPassword) {
        return new PdfEncryption(userPassword, normalizePassword(ownerPassword, "ownerPassword"), permissions);
    }

    public PdfEncryption permissions(int value) {
        return new PdfEncryption(userPassword, ownerPassword, value);
    }

    String userPassword() {
        return userPassword;
    }

    String ownerPassword() {
        return ownerPassword;
    }

    int permissions() {
        return permissions;
    }

    private static String normalizePassword(String password, String name) {
        Objects.requireNonNull(password, name);
        var stripped = password.strip();
        if (stripped.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be empty");
        }
        var nfc = java.text.Normalizer.normalize(stripped, java.text.Normalizer.Form.NFC);
        if (nfc.codePoints().anyMatch(code -> code < 0x20 || code == 0x7f)) {
            throw new IllegalArgumentException(name + " must not contain control characters");
        }
        var utf8 = nfc.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        if (utf8.length > 127) {
            throw new IllegalArgumentException(name + " exceeds the PDF 127-byte limit");
        }
        return nfc;
    }
}
