package no.beint.skald.layout.internal;

import no.beint.skald.barcode.BarcodeForm;
import no.beint.skald.colors.Color;
import no.beint.skald.font.PdfFont;
import no.beint.skald.font.PdfFontFactory;
import no.beint.skald.image.ImageData;
import no.beint.skald.image.ImageSource;
import no.beint.skald.layout.borders.Border;
import no.beint.skald.layout.properties.TextAlignment;
import no.beint.skald.pdf.PdfDocument;
import no.beint.skald.pdf.PdfPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState;
import org.apache.pdfbox.util.Matrix;

import java.io.IOException;

public final class PdfDrawing {
    private PdfDrawing() {
    }

    public static void text(PdfDocument document, PdfPage page, String value, PdfFont font, float fontSize,
                            Color color, float x, float baseline, float width, TextAlignment alignment,
                            float rotation, float opacity) {
        var text = font.supportedText(value);
        var textWidth = font.getWidth(text, fontSize);
        var alignedX = switch (alignment) {
            case LEFT -> x;
            case CENTER -> x + (width - textWidth) / 2f;
            case RIGHT -> x + width - textWidth;
        };
        try {
            var stream = append(document, page);
            stream.saveGraphicsState();
            applyOpacity(stream, opacity);
            stream.beginText();
            stream.setFont(font.pdfBoxFont(), fontSize);
            stream.setNonStrokingColor(color.red(), color.green(), color.blue());
            if (rotation == 0) {
                stream.newLineAtOffset(alignedX, baseline);
            } else {
                stream.setTextMatrix(Matrix.getRotateInstance(rotation, alignedX, baseline));
            }
            stream.showText(text);
            stream.endText();
            stream.restoreGraphicsState();
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to draw PDF text", exception);
        }
    }

    public static void fill(PdfDocument document, PdfPage page, Color color, float x, float y, float width,
                            float height, float opacity) {
        if (width <= 0 || height <= 0) {
            return;
        }
        try {
            var stream = append(document, page);
            stream.saveGraphicsState();
            applyOpacity(stream, opacity);
            stream.setNonStrokingColor(color.red(), color.green(), color.blue());
            stream.addRect(x, y, width, height);
            stream.fill();
            stream.restoreGraphicsState();
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to fill PDF rectangle", exception);
        }
    }

    public static void line(PdfDocument document, PdfPage page, Color color, float width, float x1, float y1,
                            float x2, float y2) {
        if (width <= 0) {
            return;
        }
        try {
            var stream = append(document, page);
            stream.setStrokingColor(color.red(), color.green(), color.blue());
            stream.setLineWidth(width);
            stream.moveTo(x1, y1);
            stream.lineTo(x2, y2);
            stream.stroke();
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to draw PDF line", exception);
        }
    }

    public static void borders(PdfDocument document, PdfPage page, Border top, Border right, Border bottom,
                               Border left, float x, float y, float width, float height) {
        if (top != null && top.visible()) {
            line(document, page, top.color(), top.width(), x, y + height, x + width, y + height);
        }
        if (right != null && right.visible()) {
            line(document, page, right.color(), right.width(), x + width, y, x + width, y + height);
        }
        if (bottom != null && bottom.visible()) {
            line(document, page, bottom.color(), bottom.width(), x, y, x + width, y);
        }
        if (left != null && left.visible()) {
            line(document, page, left.color(), left.width(), x, y, x, y + height);
        }
    }

    public static void image(PdfDocument document, PdfPage page, ImageSource source, float x, float y,
                             float width, float height) {
        switch (source) {
            case ImageData raster -> raster(document, page, raster, x, y, width, height);
            case BarcodeForm barcode -> barcode(document, page, barcode, x, y, width, height);
            default -> throw new IllegalArgumentException("Unsupported image source: " + source.getClass().getName());
        }
    }

    private static void raster(PdfDocument document, PdfPage page, ImageData image, float x, float y,
                               float width, float height) {
        try {
            var stream = append(document, page);
            var pdfImage = PDImageXObject.createFromByteArray(document.backingDocument(), image.bytes(), "image");
            stream.drawImage(pdfImage, x, y, width, height);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to draw PDF image", exception);
        }
    }

    private static void barcode(PdfDocument document, PdfPage page, BarcodeForm barcode, float x, float y,
                                float width, float height) {
        var sourceWidth = barcode.intrinsicWidth();
        var sourceHeight = barcode.intrinsicHeight();
        var scaleX = width / sourceWidth;
        var scaleY = height / sourceHeight;
        var textHeight = barcode.fontSize() * scaleY + 2f;
        var barBottom = y + textHeight;
        var moduleWidth = barcode.moduleWidth() * scaleX;
        var modules = barcode.modules();
        var runStart = -1;
        for (int index = 0; index <= modules.length; index++) {
            var black = index < modules.length && modules[index] == 1;
            if (black && runStart < 0) {
                runStart = index;
            } else if (!black && runStart >= 0) {
                fill(document, page, no.beint.skald.colors.ColorConstants.BLACK,
                    x + runStart * moduleWidth, barBottom, (index - runStart) * moduleWidth,
                    barcode.barHeight() * scaleY, 1f);
                runStart = -1;
            }
        }
        var fontSize = barcode.fontSize() * scaleY;
        text(document, page, barcode.code(), PdfFontFactory.regular(), fontSize,
            no.beint.skald.colors.ColorConstants.BLACK, x, y + 1f, width, TextAlignment.CENTER, 0, 1f);
    }

    private static PDPageContentStream append(PdfDocument document, PdfPage page) {
        return document.contentStream(page);
    }

    private static void applyOpacity(PDPageContentStream stream, float opacity) throws IOException {
        if (opacity >= 1f) {
            return;
        }
        var state = new PDExtendedGraphicsState();
        state.setNonStrokingAlphaConstant(opacity);
        state.setStrokingAlphaConstant(opacity);
        stream.setGraphicsStateParameters(state);
    }
}
