package org.skaldpdf.image;

import org.skaldpdf.pdf.PdfDocument;
import org.skaldpdf.pdf.PdfPage;

/**
 * A drawable image-like value. Implementations can live in optional modules,
 * allowing layout to render raster, vector, or generated content without a
 * dependency on the module that created it.
 */
public interface ImageSource {
    float intrinsicWidth();

    float intrinsicHeight();

    void drawOn(PdfDocument document, PdfPage page, float x, float y, float width, float height);
}
