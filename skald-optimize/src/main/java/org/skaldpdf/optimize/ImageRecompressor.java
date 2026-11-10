package org.skaldpdf.optimize;

import org.skaldpdf.image.ImageData;
import org.skaldpdf.pdf.EmbeddedImage;

import java.util.Optional;

/**
 * Optional image-codec hook for the PDF graph rewriter. Applications can use
 * JPEGli or another encoder without coupling Skald PDF to that runtime.
 */
@FunctionalInterface
public interface ImageRecompressor {
    /** Encoding produced by this adapter; existing JPEG adapters need no changes. */
    default org.skaldpdf.image.ImageEncoding encoding() { return org.skaldpdf.image.ImageEncoding.JPEG; }

    /** Returns an opaque one- or three-component encoded image, or empty to keep the original stream. */
    Optional<ImageData> recompress(EmbeddedImage image, OptimizeOptions options);
}
