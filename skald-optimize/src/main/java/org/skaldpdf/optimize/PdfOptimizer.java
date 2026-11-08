package org.skaldpdf.optimize;

import org.skaldpdf.codec.RasterImages;
import org.skaldpdf.image.ImageData;
import org.skaldpdf.pdf.CanonicalRewriteConstraint;
import org.skaldpdf.pdf.EmbeddedImage;
import org.skaldpdf.pdf.Compression;
import org.skaldpdf.pdf.PdfDocument;
import org.skaldpdf.pdf.PdfReader;
import org.skaldpdf.pdf.PdfWriter;
import org.skaldpdf.pdf.WriterProperties;

import java.io.ByteArrayOutputStream;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Recompresses image XObjects inside a received PDF. Generation-time helpers
 * ({@link RasterImages#scaleToFit}, {@code NativeImages.prepare}) stay on the
 * create path; this module is for files that already arrived as PDF.
 *
 * <p>JPEG XL is not written into the file. ISO 32000-2 has no interoperable
 * JXL filter, so replacements are DCT or Flate only.
 *
 * <p>The safe entry points return the original array when parsing, image
 * conversion, post-write verification, or configured savings gates reject a
 * candidate. They also preserve linearized, incrementally updated, signed and
 * malformed documents byte for byte. A declared conformance profile is not
 * protected unless {@link OptimizeOptions#preserveConformanceProfiles()} asks for it:
 * the claim is usually unverified, and the rewrite does not keep it valid.
 */
public final class PdfOptimizer {
    private static final int MINIMUM_EDGE = 32;

    private PdfOptimizer() {
    }

    public static byte[] recompress(byte[] pdf) {
        return recompress(pdf, OptimizeOptions.attachments());
    }

    /**
     * Recompresses with the JDK encoder and returns the original array unless a
     * verified candidate satisfies every configured size gate.
     */
    public static byte[] recompress(byte[] pdf, OptimizeOptions options) {
        return recompress(pdf, options, PdfOptimizer::recompressWithJdk);
    }

    /**
     * Rewrites with an application-supplied image encoder. The PDF parser,
     * graph preservation, size gates, and post-write equivalence proof remain
     * owned by Skald. Exceptions from an individual codec call keep that image
     * unchanged; a document-level failure returns the original array.
     */
    public static byte[] recompress(byte[] pdf, OptimizeOptions options, ImageRecompressor recompressor) {
        Objects.requireNonNull(pdf, "pdf");
        Objects.requireNonNull(options, "options");
        Objects.requireNonNull(recompressor, "recompressor");
        var output = new ByteArrayOutputStream(pdf.length);
        try {
            var writer = new PdfWriter(output, new WriterProperties(Compression.MAXIMUM));
            try (var document = new PdfDocument(new PdfReader(pdf), writer)) {
                var constraints = document.canonicalRewriteConstraints();
                if (!(constraints.isEmpty() || !options.preserveConformanceProfiles()
                    && constraints.equals(Set.of(CanonicalRewriteConstraint.CONFORMANCE_PROFILE)))) {
                    return pdf;
                }
                if (options.compressStreamsLosslessly()) {
                    document.compressImportedStreamsLosslessly();
                }
                if (options.deduplicateImagesLosslessly()) {
                    document.deduplicateImportedImagesLosslessly();
                }
                if (options.deduplicateFontProgramsLosslessly()) {
                    document.deduplicateImportedFontProgramsLosslessly();
                }
                if (options.removeTrueTypeHinting()) {
                    document.removeImportedTrueTypeHinting();
                }
                if (options.removeUnusedGlyphs()) {
                    document.removeImportedUnusedGlyphs();
                }
                for (var image : document.importedImages()) {
                    var target = downsampled(image, options);
                    var replacement = recompress(target.image(), target.options(), recompressor);
                    if (replacement == null) {
                        continue;
                    }
                    var resized = replacement.width() != image.width() || replacement.height() != image.height();
                    if (target.image() != image && resized) {
                        var mask = target.image().decodeSoftMask().orElse(null);
                        if (mask != null) {
                            document.replaceImportedImage(target.image(), replacement,
                                areaAverage(mask, replacement.width(), replacement.height()));
                        }
                    } else if (!resized || !image.requiresOriginalDimensions()) {
                        document.replaceImportedImage(image, replacement);
                    }
                }
            }
        } catch (RuntimeException unsupported) {
            return pdf;
        }
        var candidate = output.toByteArray();
        return options.worthReplacing(pdf.length, candidate.length) ? candidate : pdf;
    }

    private static @org.jspecify.annotations.Nullable ImageData recompress(
        EmbeddedImage image, OptimizeOptions options, ImageRecompressor recompressor
    ) {
        var pixels = (long) image.width() * image.height();
        if (!image.safeToRecompress() || pixels <= 0 || pixels > options.maximumImagePixels()
            || image.jpeg() && !options.recompressJpeg()
            || !image.jpeg() && (!options.convertLosslessRaster()
                || image.encodedLength() < options.minimumLosslessBytes())) {
            return null;
        }
        if (image.jpeg() && OptimizedJpeg.alreadySatisfies(
            image.encodedBytes(), options, image.width(), image.height(), image.requiresOriginalDimensions())) {
            return null;
        }
        ImageData data;
        try {
            data = recompressor.recompress(image, options).orElse(null);
        } catch (RuntimeException unsupported) {
            return null;
        }
        if (data == null || !data.jpeg() || data.alpha() != null
            || data.components() != 1 && data.components() != 3
            || image.requiresOriginalDimensions()
                && (data.width() != image.width() || data.height() != image.height())
        ) {
            return null;
        }
        data = OptimizedJpeg.mark(data, options,
            image.jpeg() ? options.jpegQuality() : options.losslessQuality());
        if (!options.worthReplacing(image.encodedLength(), data.samples().length)) {
            return null;
        }
        return data;
    }

    /**
     * Lowers the longest edge of an image painted above the downsampling threshold so its
     * largest placement keeps the target resolution. A logo of 2400 px painted 3 cm wide is
     * about 2000 ppi; 300 ppi is print quality, so the extra pixels only cost bytes. An image
     * with its own soft mask is returned as a view whose mask is resampled with it.
     */
    private static Target downsampled(EmbeddedImage image, OptimizeOptions options) {
        var unchanged = new Target(image, options);
        if (options.downsampleAbovePixelsPerInch() == 0 || !image.safeToRecompress()) {
            return unchanged;
        }
        var placed = image.placedPixelsPerInch();
        if (placed.isEmpty() || placed.getAsDouble() <= options.downsampleAbovePixelsPerInch()) {
            return unchanged;
        }
        var subject = image;
        if (image.requiresOriginalDimensions()) {
            subject = image.withResampledSoftMask().orElse(null);
            if (subject == null) {
                return unchanged;
            }
        }
        var longest = Math.max(image.width(), image.height());
        var edge = (int) Math.ceil(longest * options.downsampleToPixelsPerInch() / placed.getAsDouble());
        return new Target(subject, options.withMaxEdge(Math.max(MINIMUM_EDGE, Math.min(options.maxEdge(), edge))));
    }

    /** Box-filter resampling of 8-bit gray samples; each output pixel averages the area it covers. */
    static ImageData areaAverage(ImageData gray, int width, int height) {
        var source = gray.samples();
        var sourceWidth = gray.width();
        var sourceHeight = gray.height();
        var output = new byte[Math.multiplyExact(width, height)];
        for (int y = 0; y < height; y++) {
            var top = (double) y * sourceHeight / height;
            var bottom = (double) (y + 1) * sourceHeight / height;
            for (int x = 0; x < width; x++) {
                var left = (double) x * sourceWidth / width;
                var right = (double) (x + 1) * sourceWidth / width;
                double sum = 0;
                double area = 0;
                for (int sy = (int) top; sy < Math.min(sourceHeight, Math.ceil(bottom)); sy++) {
                    var coverY = Math.min(bottom, sy + 1) - Math.max(top, sy);
                    for (int sx = (int) left; sx < Math.min(sourceWidth, Math.ceil(right)); sx++) {
                        var cover = coverY * (Math.min(right, sx + 1) - Math.max(left, sx));
                        sum += cover * (source[sy * sourceWidth + sx] & 0xff);
                        area += cover;
                    }
                }
                output[y * width + x] = (byte) Math.round(area > 0 ? sum / area : 0);
            }
        }
        return ImageData.fromGray(width, height, output);
    }

    private record Target(EmbeddedImage image, OptimizeOptions options) {
    }


    private static Optional<ImageData> recompressWithJdk(EmbeddedImage image, OptimizeOptions options) {
        var maxEdge = image.requiresOriginalDimensions()
            ? Math.max(image.width(), image.height()) : options.maxEdge();
        return image.decode().map(data -> RasterImages.asJpeg(
            data, maxEdge, maxEdge,
            image.jpeg() ? options.jpegQuality() : options.losslessQuality()));
    }
}
