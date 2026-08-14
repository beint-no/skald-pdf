package org.skaldpdf.codec;

/**
 * Policy for turning a phone photo into a PDF-ready JPEG.
 *
 * @param maxEdge longest output edge in pixels
 * @param jpegQuality TurboJPEG quality 1–100
 */
public record PrepareOptions(int maxEdge, int jpegQuality) {
    public PrepareOptions {
        if (maxEdge < 32) {
            throw new IllegalArgumentException("maxEdge must be at least 32");
        }
        if (jpegQuality < 1 || jpegQuality > 100) {
            throw new IllegalArgumentException("jpegQuality must be 1-100");
        }
    }

    /** Typical expense / attachment photo. */
    public static PrepareOptions photos() {
        return new PrepareOptions(1600, 80);
    }
}
