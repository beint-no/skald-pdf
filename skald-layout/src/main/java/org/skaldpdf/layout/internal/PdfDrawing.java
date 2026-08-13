package org.skaldpdf.layout.internal;

import org.skaldpdf.colors.Color;
import org.skaldpdf.font.PdfFont;
import org.skaldpdf.image.ImageSource;
import org.skaldpdf.layout.borders.Border;
import org.skaldpdf.layout.properties.TextAlignment;
import org.skaldpdf.pdf.PdfDocument;
import org.skaldpdf.pdf.PdfPage;

import java.util.Locale;

/** Serializes the small, audited content-stream operator surface used by layout. */
public final class PdfDrawing {
    private PdfDrawing() {
    }

    public static void text(PdfDocument document, PdfPage page, String value, PdfFont font, float fontSize,
                            Color color, float x, float baseline, float width, TextAlignment alignment,
                            float rotation, float opacity) {
        document.ensureOpen();
        var run = font.glyphRun(value);
        var textWidth = run.advance() * fontSize / 1_000f;
        var alignedX = switch (alignment) {
            case LEFT -> x;
            case CENTER -> x + (width - textWidth) / 2f;
            case RIGHT -> x + width - textWidth;
        };
        var fontName = page.registerFont(font, run);
        var operators = new StringBuilder(96 + run.glyphs().length * 4).append("q\n");
        opacity(operators, page, opacity);
        operators.append(number(color.red())).append(' ').append(number(color.green())).append(' ')
            .append(number(color.blue())).append(" rg\nBT\n/").append(fontName).append(' ')
            .append(number(fontSize)).append(" Tf\n");
        if (rotation == 0) {
            operators.append("1 0 0 1 ").append(number(alignedX)).append(' ')
                .append(number(baseline)).append(" Tm\n");
        } else {
            var cosine = (float) Math.cos(rotation);
            var sine = (float) Math.sin(rotation);
            operators.append(number(cosine)).append(' ').append(number(sine)).append(' ')
                .append(number(-sine)).append(' ').append(number(cosine)).append(' ')
                .append(number(alignedX)).append(' ').append(number(baseline)).append(" Tm\n");
        }
        operators.append('<');
        for (var glyph : run.glyphs()) {
            if (glyph < 0 || glyph > 0xffff) {
                throw new IllegalArgumentException("Glyph index exceeds Identity-H encoding");
            }
            operators.append(String.format(Locale.ROOT, "%04X", glyph));
        }
        page.append(operators.append("> Tj\nET\nQ\n").toString());
    }

    public static void fill(PdfDocument document, PdfPage page, Color color, float x, float y, float width,
                            float height, float opacity) {
        if (width <= 0 || height <= 0) {
            return;
        }
        document.ensureOpen();
        var operators = new StringBuilder("q\n");
        opacity(operators, page, opacity);
        operators.append(number(color.red())).append(' ').append(number(color.green())).append(' ')
            .append(number(color.blue())).append(" rg\n")
            .append(number(x)).append(' ').append(number(y)).append(' ')
            .append(number(width)).append(' ').append(number(height)).append(" re f\nQ\n");
        page.append(operators.toString());
    }

    public static void line(PdfDocument document, PdfPage page, Color color, float width, float x1, float y1,
                            float x2, float y2) {
        if (width <= 0) {
            return;
        }
        document.ensureOpen();
        page.append(new StringBuilder("q\n")
            .append(number(color.red())).append(' ').append(number(color.green())).append(' ')
            .append(number(color.blue())).append(" RG\n")
            .append(number(width)).append(" w\n")
            .append(number(x1)).append(' ').append(number(y1)).append(" m\n")
            .append(number(x2)).append(' ').append(number(y2)).append(" l S\nQ\n").toString());
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

    public static void beginClip(PdfDocument document, PdfPage page, float x, float y, float width, float height) {
        document.ensureOpen();
        page.append(new StringBuilder("q\n")
            .append(number(x)).append(' ').append(number(y)).append(' ')
            .append(number(width)).append(' ').append(number(height)).append(" re W n\n").toString());
    }

    public static void endGraphicsState(PdfDocument document, PdfPage page) {
        document.ensureOpen();
        page.append("Q\n");
    }

    public static void image(PdfDocument document, PdfPage page, ImageSource source, float x, float y,
                             float width, float height) {
        source.drawOn(document, page, x, y, width, height);
    }

    private static void opacity(StringBuilder operators, PdfPage page, float opacity) {
        if (opacity < 0 || opacity > 1 || !Float.isFinite(opacity)) {
            throw new IllegalArgumentException("Opacity must be finite and between 0 and 1");
        }
        if (opacity < 1f) {
            operators.append('/').append(page.registerOpacity(opacity)).append(" gs\n");
        }
    }

    private static String number(float value) {
        if (!Float.isFinite(value)) {
            throw new IllegalArgumentException("PDF number must be finite");
        }
        if (value == Math.rint(value) && value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE) {
            return Integer.toString((int) value);
        }
        var result = String.format(Locale.ROOT, "%.5f", value);
        return result.replaceFirst("0+$", "").replaceFirst("\\.$", "");
    }
}
