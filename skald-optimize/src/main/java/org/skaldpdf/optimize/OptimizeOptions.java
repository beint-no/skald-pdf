package org.skaldpdf.optimize;

/**
 * Policy for rewriting image XObjects already stored in a received PDF.
 *
 * @param maxEdge longest allowed edge in pixels after downsampling
 * @param jpegQuality ImageIO JPEG quality in {@code (0, 1]}
 * @param recompressJpeg whether existing DCT streams may be re-encoded
 */
public record OptimizeOptions(int maxEdge, float jpegQuality, boolean recompressJpeg) {
    public OptimizeOptions {
        if (maxEdge < 32) {
            throw new IllegalArgumentException("maxEdge must be at least 32");
        }
        if (!(jpegQuality > 0) || jpegQuality > 1 || !Float.isFinite(jpegQuality)) {
            throw new IllegalArgumentException("jpegQuality must be in (0, 1]");
        }
    }

    /** Typical supplier invoice / expense attachment. */
    public static OptimizeOptions attachments() {
        return new OptimizeOptions(1600, 0.80f, true);
    }
}
