package org.skaldpdf.pdf;

import java.util.Objects;

/**
 * The sole raster image drawn by a single-page imported PDF that contains no
 * other visible content, text, vector graphics, annotations, forms, signatures,
 * embedded files, or cropping clip paths.
 *
 * <p>{@link #exifOrientation()} is an EXIF orientation tag ({@code 1}–{@code 8})
 * mapping the image's raw stored pixel grid (ignoring any EXIF metadata inside
 * a JPEG stream, as ISO 32000 requires) onto the upright visual page after
 * combining the PDF image transformation matrix and page {@code /Rotate}.
 */
public record SinglePageImage(EmbeddedImage image, int exifOrientation) {
    public SinglePageImage {
        Objects.requireNonNull(image, "image");
        if (exifOrientation < 1 || exifOrientation > 8) {
            throw new IllegalArgumentException("EXIF orientation must be between 1 and 8: " + exifOrientation);
        }
    }
}
