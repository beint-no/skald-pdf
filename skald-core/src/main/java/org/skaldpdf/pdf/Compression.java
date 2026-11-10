package org.skaldpdf.pdf;

/** Compression policies for newly written PDF streams. Flate remains the default. */
public enum Compression {
    NONE(0),
    FAST(1),
    BALANCED(6),
    MAXIMUM(9),
    /** Opt-in Brotli, quality 1. Requires a Brotli-capable PDF viewer. */
    BROTLI_FAST(1, 1),
    /** Opt-in Brotli, quality 6. Requires a Brotli-capable PDF viewer. */
    BROTLI_BALANCED(6, 6),
    /** Opt-in Brotli, quality 11. Encoding may be expensive. */
    BROTLI_MAXIMUM(9, 11);

    private final int deflateLevel;
    private final int brotliQuality;

    Compression(int deflateLevel) {
        this(deflateLevel, -1);
    }

    Compression(int deflateLevel, int brotliQuality) {
        this.deflateLevel = deflateLevel;
        this.brotliQuality = brotliQuality;
    }

    /** Whether the encoder required by this policy is available. */
    public boolean available() {
        return !brotli() || BrotliCodecs.canEncode();
    }

    /** Whether the optional Brotli decoder is available for imported PDF streams. */
    public static boolean brotliDecodingAvailable() {
        return BrotliCodecs.canDecode();
    }

    int deflateLevel() {
        return deflateLevel;
    }

    boolean brotli() {
        return brotliQuality >= 0;
    }

    byte[] brotliEncode(byte[] bytes) {
        return BrotliCodecs.encode(bytes, brotliQuality);
    }
}
