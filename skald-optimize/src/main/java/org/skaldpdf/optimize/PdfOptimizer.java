package org.skaldpdf.optimize;

import org.skaldpdf.image.ImageData;
import org.skaldpdf.pdf.EmbeddedImage;
import org.skaldpdf.pdf.PdfDocument;
import org.skaldpdf.pdf.PdfReader;
import org.skaldpdf.pdf.PdfWriter;

import java.io.ByteArrayOutputStream;
import java.util.Objects;

/**
 * Recompresses image XObjects inside a received PDF. Generation-time helpers
 * ({@code ImageData.scaledToFit}, {@code NativeImages.prepare}) stay on the
 * create path; this module is for files that already arrived as PDF.
 *
 * <p>JPEG XL is not written into the file. ISO 32000-2 has no interoperable
 * JXL filter, so replacements are DCT or Flate only.
 */
public final class PdfOptimizer {
    private PdfOptimizer() {
    }

    public static byte[] recompress(byte[] pdf) {
        return recompress(pdf, OptimizeOptions.attachments());
    }

    public static byte[] recompress(byte[] pdf, OptimizeOptions options) {
        Objects.requireNonNull(pdf, "pdf");
        Objects.requireNonNull(options, "options");
        var output = new ByteArrayOutputStream(pdf.length);
        try (var document = new PdfDocument(new PdfReader(pdf), new PdfWriter(output))) {
            for (var image : document.importedImages()) {
                var replacement = recompress(image, options);
                if (replacement != null) {
                    document.replaceImportedImage(image.pageNumber(), image.resourceName(), replacement);
                }
            }
        }
        return output.toByteArray();
    }

    private static ImageData recompress(EmbeddedImage image, OptimizeOptions options) {
        var decoded = image.decode();
        if (decoded.isEmpty()) {
            return null;
        }
        var data = decoded.get();
        var longest = Math.max(data.width(), data.height());
        var scaled = longest > options.maxEdge();
        if (scaled) {
            data = data.scaledToFit(options.maxEdge(), options.maxEdge());
        }
        if (!data.jpeg() || options.recompressJpeg() || scaled) {
            data = data.asJpeg(options.jpegQuality());
        } else {
            return null;
        }
        if (!scaled && data.samples().length >= image.encodedLength()) {
            return null;
        }
        return data;
    }
}
