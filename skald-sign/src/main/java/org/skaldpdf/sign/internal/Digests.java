package org.skaldpdf.sign.internal;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class Digests {
    private Digests() {
    }

    public static byte[] sha256(byte[] data) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(data);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is required", impossible);
        }
    }

    public static byte[] sha256(byte[] data, int offset, int length) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            digest.update(data, offset, length);
            return digest.digest();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is required", impossible);
        }
    }
}
