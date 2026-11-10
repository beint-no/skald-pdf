package org.skaldpdf.optimize;

import org.skaldpdf.pdf.Compression;

import java.util.Objects;

/** Immutable policy for image recompression inside received PDFs. */
public final class OptimizeOptions {
    private final boolean experimentalJpegXl;
    private final Compression streamCompression;
    private final int maxEdge;
    private final float jpegQuality;
    private final float losslessQuality;
    private final boolean recompressJpeg;
    private final boolean convertLosslessRaster;
    private final boolean compressStreamsLosslessly;
    private final boolean deduplicateImagesLosslessly;
    private final boolean deduplicateFontProgramsLosslessly;
    private final boolean preserveConformanceProfiles;
    private final long maximumImagePixels;
    private final int minimumLosslessBytes;
    private final int minimumSavingsBytes;
    private final int minimumSavingsPercent;
    private final int downsampleAbovePixelsPerInch;
    private final int downsampleToPixelsPerInch;
    private final boolean removeTrueTypeHinting;
    private final boolean removeUnusedGlyphs;

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
        experimentalJpegXl = builder.experimentalJpegXl;
        streamCompression = builder.streamCompression;
        maxEdge = builder.maxEdge;
        jpegQuality = builder.jpegQuality;
        losslessQuality = builder.losslessQuality;
        recompressJpeg = builder.recompressJpeg;
        convertLosslessRaster = builder.convertLosslessRaster;
        compressStreamsLosslessly = builder.compressStreamsLosslessly;
        deduplicateImagesLosslessly = builder.deduplicateImagesLosslessly;
        deduplicateFontProgramsLosslessly = builder.deduplicateFontProgramsLosslessly;
        preserveConformanceProfiles = builder.preserveConformanceProfiles;
        maximumImagePixels = builder.maximumImagePixels;
        minimumLosslessBytes = builder.minimumLosslessBytes;
        minimumSavingsBytes = builder.minimumSavingsBytes;
        minimumSavingsPercent = builder.minimumSavingsPercent;
        downsampleAbovePixelsPerInch = builder.downsampleAbovePixelsPerInch;
        downsampleToPixelsPerInch = builder.downsampleToPixelsPerInch;
        removeTrueTypeHinting = builder.removeTrueTypeHinting;
        removeUnusedGlyphs = builder.removeUnusedGlyphs;
        validate();
    }

    /**
     * Supplier invoices, receipts, and other camera-heavy business attachments:
     * 2400 px, JPEG quality 75, lossless-raster quality 90, 20 MP, exact
     * stream compression, exact image/font-program sharing, a 16 KiB
     * lossless-raster work floor, 4 KiB / 2% savings gates, and images painted
     * above 450 ppi downsampled to 300 ppi of their largest placement, and
     * TrueType hinting, layout tables and the data of glyphs no page draws
     * removed from embedded fonts.
     */
    public static OptimizeOptions attachments() {
        return builder().build();
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Whether provisional JPEG XL image output is explicitly permitted. */
    public boolean experimentalJpegXl() { return experimentalJpegXl; }

    /** Codec for new and eligible losslessly recompressed streams; Flate MAXIMUM by default. */
    public Compression streamCompression() {
        return streamCompression;
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

    /** Whether byte-identical embedded font program streams should share one object. */
    public boolean deduplicateFontProgramsLosslessly() {
        return deduplicateFontProgramsLosslessly;
    }

    /**
     * Whether a declared PDF/A, PDF/X, PDF/UA, PDF/E or PDF/VT profile keeps the
     * document byte for byte. The rewrite does not preserve profile conformance.
     */
    public boolean preserveConformanceProfiles() {
        return preserveConformanceProfiles;
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

    /**
     * Images whose largest placement on the page exceeds this resolution are
     * downsampled to {@link #downsampleToPixelsPerInch()}; 0 disables it. Images
     * with a preserved soft mask or any use that cannot be followed keep their size.
     */
    public int downsampleAbovePixelsPerInch() {
        return downsampleAbovePixelsPerInch;
    }

    /** Target resolution, in pixels per inch of the largest placement, for downsampled images. */
    public int downsampleToPixelsPerInch() {
        return downsampleToPixelsPerInch;
    }

    /**
     * Whether embedded TrueType programs lose hinting instructions and the layout
     * tables PDF rendering never reads. Text, glyph outlines and metrics are unchanged.
     */
    public boolean removeTrueTypeHinting() {
        return removeTrueTypeHinting;
    }

    /**
     * Whether TrueType programs of composite Identity fonts lose the outlines and
     * metrics of glyphs no page draws. Glyph IDs and every drawn glyph are unchanged.
     */
    public boolean removeUnusedGlyphs() {
        return removeUnusedGlyphs;
    }

    /** This policy with a smaller longest edge for one image. */
    OptimizeOptions withMaxEdge(int value) {
        if (value == maxEdge) {
            return this;
        }
        var builder = builder().experimentalJpegXl(experimentalJpegXl).streamCompression(streamCompression).maxEdge(value).jpegQuality(jpegQuality).losslessQuality(losslessQuality)
            .recompressJpeg(recompressJpeg).convertLosslessRaster(convertLosslessRaster)
            .compressStreamsLosslessly(compressStreamsLosslessly)
            .deduplicateImagesLosslessly(deduplicateImagesLosslessly)
            .deduplicateFontProgramsLosslessly(deduplicateFontProgramsLosslessly)
            .preserveConformanceProfiles(preserveConformanceProfiles).maximumImagePixels(maximumImagePixels)
            .minimumLosslessBytes(minimumLosslessBytes).minimumSavingsBytes(minimumSavingsBytes)
            .minimumSavingsPercent(minimumSavingsPercent)
            .downsampleAbovePixelsPerInch(downsampleAbovePixelsPerInch)
            .downsampleToPixelsPerInch(downsampleToPixelsPerInch)
            .removeTrueTypeHinting(removeTrueTypeHinting)
            .removeUnusedGlyphs(removeUnusedGlyphs);
        return builder.build();
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
        if (downsampleAbovePixelsPerInch != 0 && (downsampleToPixelsPerInch < 72
            || downsampleAbovePixelsPerInch < downsampleToPixelsPerInch)) {
            throw new IllegalArgumentException(
                "downsampleToPixelsPerInch must be at least 72 and at most downsampleAbovePixelsPerInch");
        }
    }

    private static void quality(float value, String name) {
        if (!(value > 0) || value > 1 || !Float.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be in (0, 1]");
        }
    }

    public static final class Builder {
        private boolean experimentalJpegXl;
        /** Explicitly permits provisional JPEG XL PDF output. */
        public Builder experimentalJpegXl(boolean value) { experimentalJpegXl = value; return this; }

        private Compression streamCompression = Compression.MAXIMUM;
        private int maxEdge = 2400;
        private float jpegQuality = 0.75f;
        private float losslessQuality = 0.90f;
        private boolean recompressJpeg = true;
        private boolean convertLosslessRaster = true;
        private boolean compressStreamsLosslessly = true;
        private boolean deduplicateImagesLosslessly = true;
        private boolean deduplicateFontProgramsLosslessly = true;
        private boolean preserveConformanceProfiles;
        private long maximumImagePixels = 20_000_000;
        private int minimumLosslessBytes = 16 * 1024;
        private int minimumSavingsBytes = 4096;
        private int minimumSavingsPercent = 2;
        private int downsampleAbovePixelsPerInch = 450;
        private int downsampleToPixelsPerInch = 300;
        private boolean removeTrueTypeHinting = true;
        private boolean removeUnusedGlyphs = true;

        private Builder() {
        }

        /** Opts into a stream codec. Brotli needs skald-brotli and compatible viewers. */
        public Builder streamCompression(Compression value) {
            streamCompression = Objects.requireNonNull(value, "value");
            return this;
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

        /** Enables exact sharing of byte-identical embedded font program streams. */
        public Builder deduplicateFontProgramsLosslessly(boolean value) {
            deduplicateFontProgramsLosslessly = value;
            return this;
        }

        /** Leaves documents that declare a conformance profile untouched instead of optimizing them. */
        public Builder preserveConformanceProfiles(boolean value) {
            preserveConformanceProfiles = value;
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

        /** Downsample images painted above this resolution; 0 keeps every image's resolution. */
        public Builder downsampleAbovePixelsPerInch(int value) {
            downsampleAbovePixelsPerInch = value;
            return this;
        }

        public Builder downsampleToPixelsPerInch(int value) {
            downsampleToPixelsPerInch = value;
            return this;
        }

        public Builder removeTrueTypeHinting(boolean value) {
            removeTrueTypeHinting = value;
            return this;
        }

        public Builder removeUnusedGlyphs(boolean value) {
            removeUnusedGlyphs = value;
            return this;
        }

        public OptimizeOptions build() {
            return new OptimizeOptions(this);
        }
    }
}
