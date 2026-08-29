package org.skaldpdf.optimize;

/** Immutable policy for image recompression inside received PDFs. */
public final class OptimizeOptions {
    private final int maxEdge;
    private final float jpegQuality;
    private final float losslessQuality;
    private final boolean recompressJpeg;
    private final boolean convertLosslessRaster;
    private final boolean compressStreamsLosslessly;
    private final boolean deduplicateImagesLosslessly;
    private final long maximumImagePixels;
    private final int minimumLosslessBytes;
    private final int minimumSavingsBytes;
    private final int minimumSavingsPercent;

    /**
     * Compatibility constructor for the original Skald optimizer API. Lossless
     * rasters use the same quality and all positive per-image savings qualify.
     */
    public OptimizeOptions(int maxEdge, float jpegQuality, boolean recompressJpeg) {
        this(builder().maxEdge(maxEdge).jpegQuality(jpegQuality).losslessQuality(jpegQuality)
            .recompressJpeg(recompressJpeg).minimumLosslessBytes(0)
            .minimumSavingsBytes(1).minimumSavingsPercent(0));
    }

    private OptimizeOptions(Builder builder) {
        maxEdge = builder.maxEdge;
        jpegQuality = builder.jpegQuality;
        losslessQuality = builder.losslessQuality;
        recompressJpeg = builder.recompressJpeg;
        convertLosslessRaster = builder.convertLosslessRaster;
        compressStreamsLosslessly = builder.compressStreamsLosslessly;
        deduplicateImagesLosslessly = builder.deduplicateImagesLosslessly;
        maximumImagePixels = builder.maximumImagePixels;
        minimumLosslessBytes = builder.minimumLosslessBytes;
        minimumSavingsBytes = builder.minimumSavingsBytes;
        minimumSavingsPercent = builder.minimumSavingsPercent;
        validate();
    }

    /**
     * Supplier invoices, receipts, and other camera-heavy business attachments:
     * 2400 px, JPEG quality 80, lossless-raster quality 90, 20 MP, exact
     * stream/image sharing, and 4 KiB / 2% savings gates.
     */
    public static OptimizeOptions attachments() {
        return builder().build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public int maxEdge() {
        return maxEdge;
    }

    public float jpegQuality() {
        return jpegQuality;
    }

    public float losslessQuality() {
        return losslessQuality;
    }

    public boolean recompressJpeg() {
        return recompressJpeg;
    }

    public boolean convertLosslessRaster() {
        return convertLosslessRaster;
    }

    /**
     * Whether eligible unfiltered and Flate-encoded streams should be
     * compressed without changing their decoded bytes.
     */
    public boolean compressStreamsLosslessly() {
        return compressStreamsLosslessly;
    }

    /** Whether byte-identical simple image XObjects should share one stream. */
    public boolean deduplicateImagesLosslessly() {
        return deduplicateImagesLosslessly;
    }

    public long maximumImagePixels() {
        return maximumImagePixels;
    }

    public int minimumLosslessBytes() {
        return minimumLosslessBytes;
    }

    public int minimumSavingsBytes() {
        return minimumSavingsBytes;
    }

    public int minimumSavingsPercent() {
        return minimumSavingsPercent;
    }

    boolean worthReplacing(long originalBytes, long candidateBytes) {
        var saved = originalBytes - candidateBytes;
        return saved >= minimumSavingsBytes
            && saved * 100 >= originalBytes * minimumSavingsPercent;
    }

    int markerFingerprint() {
        var result = maxEdge;
        result = 31 * result + Float.floatToIntBits(jpegQuality);
        result = 31 * result + Float.floatToIntBits(losslessQuality);
        result = 31 * result + Boolean.hashCode(recompressJpeg);
        result = 31 * result + Boolean.hashCode(convertLosslessRaster);
        return result;
    }

    private void validate() {
        if (maxEdge < 32) {
            throw new IllegalArgumentException("maxEdge must be at least 32");
        }
        quality(jpegQuality, "jpegQuality");
        quality(losslessQuality, "losslessQuality");
        if (maximumImagePixels < 1 || minimumLosslessBytes < 0 || minimumSavingsBytes < 0
            || minimumSavingsPercent < 0 || minimumSavingsPercent > 100) {
            throw new IllegalArgumentException("Image limits and savings thresholds are outside their supported range");
        }
    }

    private static void quality(float value, String name) {
        if (!(value > 0) || value > 1 || !Float.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be in (0, 1]");
        }
    }

    public static final class Builder {
        private int maxEdge = 2400;
        private float jpegQuality = 0.80f;
        private float losslessQuality = 0.90f;
        private boolean recompressJpeg = true;
        private boolean convertLosslessRaster = true;
        private boolean compressStreamsLosslessly = true;
        private boolean deduplicateImagesLosslessly = true;
        private long maximumImagePixels = 20_000_000;
        private int minimumLosslessBytes = 64 * 1024;
        private int minimumSavingsBytes = 4096;
        private int minimumSavingsPercent = 2;

        private Builder() {
        }

        public Builder maxEdge(int value) {
            maxEdge = value;
            return this;
        }

        public Builder jpegQuality(float value) {
            jpegQuality = value;
            return this;
        }

        public Builder losslessQuality(float value) {
            losslessQuality = value;
            return this;
        }

        public Builder recompressJpeg(boolean value) {
            recompressJpeg = value;
            return this;
        }

        public Builder convertLosslessRaster(boolean value) {
            convertLosslessRaster = value;
            return this;
        }

        /** Enables byte-exact compression of eligible PDF streams. */
        public Builder compressStreamsLosslessly(boolean value) {
            compressStreamsLosslessly = value;
            return this;
        }

        /** Enables exact sharing of byte-identical, semantically simple image XObjects. */
        public Builder deduplicateImagesLosslessly(boolean value) {
            deduplicateImagesLosslessly = value;
            return this;
        }

        public Builder maximumImagePixels(long value) {
            maximumImagePixels = value;
            return this;
        }

        public Builder minimumLosslessBytes(int value) {
            minimumLosslessBytes = value;
            return this;
        }

        public Builder minimumSavingsBytes(int value) {
            minimumSavingsBytes = value;
            return this;
        }

        public Builder minimumSavingsPercent(int value) {
            minimumSavingsPercent = value;
            return this;
        }

        public OptimizeOptions build() {
            return new OptimizeOptions(this);
        }
    }
}
