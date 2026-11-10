package org.skaldpdf.codec;

/** JPEG XL perceptual distance (lower is better; zero is lossless) and encoder effort. */
public record JpegXlOptions(float distance, int effort) {
    public JpegXlOptions {
        if (!Float.isFinite(distance)
                || distance < 0
                || distance > 25
                || effort < 1
                || effort > 9) {
            throw new IllegalArgumentException("JPEG XL needs distance 0..25 and effort 1..9");
        }
    }

    /** Exact samples, or reversible JPEG recompression when the input is a JPEG. */
    public static JpegXlOptions lossless() {
        return new JpegXlOptions(0, 7);
    }

    /** High quality photographic encoding. */
    public static JpegXlOptions highQuality() {
        return new JpegXlOptions(0.5f, 7);
    }

    /** Balanced photographic encoding. */
    public static JpegXlOptions photos() {
        return new JpegXlOptions(1, 7);
    }

    /** Smaller photographic encoding, with greater distortion. */
    public static JpegXlOptions compact() {
        return new JpegXlOptions(2, 7);
    }
}
