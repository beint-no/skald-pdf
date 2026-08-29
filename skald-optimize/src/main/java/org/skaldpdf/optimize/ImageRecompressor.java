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
    /** Returns an opaque one- or three-component JPEG, or empty to keep the original stream. */
    Optional<ImageData> recompress(EmbeddedImage image, OptimizeOptions options);
}
