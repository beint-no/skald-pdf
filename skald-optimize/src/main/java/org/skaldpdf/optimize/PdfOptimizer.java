package org.skaldpdf.optimize;

import org.skaldpdf.codec.RasterImages;
import org.skaldpdf.image.ImageData;
import org.skaldpdf.pdf.EmbeddedImage;
import org.skaldpdf.pdf.Compression;
import org.skaldpdf.pdf.PdfDocument;
import org.skaldpdf.pdf.PdfReader;
import org.skaldpdf.pdf.PdfWriter;
import org.skaldpdf.pdf.WriterProperties;

import java.io.ByteArrayOutputStream;
import java.util.Objects;
import java.util.Optional;

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
 * candidate. They also preserve protected document classes byte for byte.
 */
public final class PdfOptimizer {
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
                if (!document.isSafeForCanonicalOptimization()) {
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
                for (var image : document.importedImages()) {
                    var replacement = recompress(image, options, recompressor);
                    if (replacement != null) {
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

    private static Optional<ImageData> recompressWithJdk(EmbeddedImage image, OptimizeOptions options) {
        var maxEdge = image.requiresOriginalDimensions()
            ? Math.max(image.width(), image.height()) : options.maxEdge();
        return image.decode().map(data -> RasterImages.asJpeg(
            data, maxEdge, maxEdge,
            image.jpeg() ? options.jpegQuality() : options.losslessQuality()));
    }
}
