package org.skaldpdf.layout.internal;

import org.skaldpdf.colors.Color;
import org.skaldpdf.colors.LinearGradient;
import org.skaldpdf.font.PdfFont;
import org.skaldpdf.geom.Rectangle;
import org.skaldpdf.image.ImageSource;
import org.skaldpdf.layout.borders.Border;
import org.skaldpdf.layout.properties.TextAlignment;
import org.skaldpdf.pdf.PdfDocument;
import org.skaldpdf.pdf.PdfNumbers;
import org.skaldpdf.pdf.PdfPage;

/** Serializes the small, audited content-stream operator surface used by layout. */
public final class PdfDrawing {
    private static final float CORNER_KAPPA = 0.55228475f;

    private PdfDrawing() {
    }

    public static void text(PdfDocument document, PdfPage page, String value, PdfFont font, float fontSize,
                            Color color, float x, float baseline, float width, TextAlignment alignment,
                            float rotation, float opacity) {
        document.ensureOpen();
        var run = font.glyphRun(value);
        var textWidth = run.advance() * fontSize / 1_000f;
        var alignedX = switch (alignment) {
            case LEFT, JUSTIFY -> x;
            case CENTER -> x + (width - textWidth) / 2f;
            case RIGHT -> x + width - textWidth;
        };
        var fontName = page.registerFont(font, run);
        var operators = new StringBuilder(80 + run.glyphs().length * 4);
        var isolated = opacity < 1f;
        if (isolated) {
            operators.append("q\n");
            opacity(operators, page, opacity);
        }
        rgb(operators, color).append(" rg\nBT\n/").append(fontName).append(' ');
        PdfNumbers.append(operators, fontSize);
        operators.append(" Tf\n");
        if (rotation == 0) {
            operators.append("1 0 0 1 ");
            PdfNumbers.append(operators, alignedX);
            operators.append(' ');
            PdfNumbers.append(operators, baseline);
            operators.append(" Tm\n");
        } else {
            var cosine = (float) Math.cos(rotation);
            var sine = (float) Math.sin(rotation);
            PdfNumbers.append(operators, cosine);
            operators.append(' ');
            PdfNumbers.append(operators, sine);
            operators.append(' ');
            PdfNumbers.append(operators, -sine);
            operators.append(' ');
            PdfNumbers.append(operators, cosine);
            operators.append(' ');
            PdfNumbers.append(operators, alignedX);
            operators.append(' ');
            PdfNumbers.append(operators, baseline);
            operators.append(" Tm\n");
        }
        operators.append('<');
        for (var glyph : run.glyphs()) {
            PdfNumbers.appendHex4(operators, glyph);
        }
        operators.append("> Tj\nET\n");
        if (isolated) {
            operators.append("Q\n");
        }
        page.append(operators.toString());
    }

    public static void fill(PdfDocument document, PdfPage page, Color color, float x, float y, float width,
                            float height, float opacity) {
        fillRounded(document, page, color, x, y, width, height, 0, opacity);
    }

    public static void fillRounded(PdfDocument document, PdfPage page, Color color, float x, float y,
                                   float width, float height, float radius, float opacity) {
        if (width <= 0 || height <= 0) {
            return;
        }
        document.ensureOpen();
        var operators = new StringBuilder("q\n");
        opacity(operators, page, opacity);
        rgb(operators, color).append(" rg\n");
        appendRectPath(operators, x, y, width, height, radius);
        page.append(operators.append("f\nQ\n").toString());
    }

    public static void fillGradient(PdfDocument document, PdfPage page, LinearGradient gradient, float x, float y,
                                    float width, float height, float radius, float opacity) {
        if (width <= 0 || height <= 0) {
            return;
        }
        document.ensureOpen();
        var x0 = x;
        var y0 = y;
        var x1 = x;
        var y1 = y;
        if (gradient.direction() == LinearGradient.Direction.VERTICAL) {
            y0 = y + height;
        } else {
            x1 = x + width;
        }
        var shading = page.registerAxialShading(x0, y0, x1, y1, gradient.start(), gradient.end());
        var operators = new StringBuilder("q\n");
        opacity(operators, page, opacity);
        appendRectPath(operators, x, y, width, height, radius);
        operators.append("W n\n/").append(shading).append(" sh\nQ\n");
        page.append(operators.toString());
    }

    public static void line(PdfDocument document, PdfPage page, Color color, float width, float x1, float y1,
                            float x2, float y2) {
        line(document, page, color, width, x1, y1, x2, y2, 0, 0);
    }

    public static void line(PdfDocument document, PdfPage page, Color color, float width, float x1, float y1,
                            float x2, float y2, float dash, float gap) {
        if (width <= 0) {
            return;
        }
        document.ensureOpen();
        var operators = new StringBuilder("q\n");
        rgb(operators, color).append(" RG\n");
        PdfNumbers.append(operators, width);
        operators.append(" w\n");
        if (dash > 0 && gap > 0) {
            operators.append('[');
            PdfNumbers.append(operators, dash);
            operators.append(' ');
            PdfNumbers.append(operators, gap);
            operators.append("] 0 d\n1 J\n");
        }
        PdfNumbers.append(operators, x1);
        operators.append(' ');
        PdfNumbers.append(operators, y1);
        operators.append(" m\n");
        PdfNumbers.append(operators, x2);
        operators.append(' ');
        PdfNumbers.append(operators, y2);
        page.append(operators.append(" l S\nQ\n").toString());
    }

    public static void disc(PdfDocument document, PdfPage page, Color color, float cx, float cy, float radius) {
        if (radius <= 0) {
            return;
        }
        document.ensureOpen();
        var operators = new StringBuilder("q\n");
        rgb(operators, color).append(" rg\n");
        appendRectPath(operators, cx - radius, cy - radius, radius * 2f, radius * 2f, radius);
        page.append(operators.append("f\nQ\n").toString());
    }

    public static void borders(PdfDocument document, PdfPage page, Border top, Border right, Border bottom,
                               Border left, float x, float y, float width, float height) {
        borders(document, page, top, right, bottom, left, x, y, width, height, 0);
    }

    public static void borders(PdfDocument document, PdfPage page, Border top, Border right, Border bottom,
                               Border left, float x, float y, float width, float height, float radius) {
        if (radius > 0 && sameStroke(top, right, bottom, left) && top != null && top.visible()) {
            document.ensureOpen();
            var operators = new StringBuilder("q\n");
            rgb(operators, top.color()).append(" RG\n");
            PdfNumbers.append(operators, top.width());
            operators.append(" w\n");
            if (top.dash() > 0 && top.gap() > 0) {
                operators.append('[');
                PdfNumbers.append(operators, top.dash());
                operators.append(' ');
                PdfNumbers.append(operators, top.gap());
                operators.append("] 0 d\n1 J\n");
            }
            appendRectPath(operators, x, y, width, height, radius);
            page.append(operators.append("S\nQ\n").toString());
            return;
        }
        if (top != null && top.visible()) {
            line(document, page, top.color(), top.width(), x, y + height, x + width, y + height, top.dash(), top.gap());
        }
        if (right != null && right.visible()) {
            line(document, page, right.color(), right.width(), x + width, y, x + width, y + height,
                right.dash(), right.gap());
        }
        if (bottom != null && bottom.visible()) {
            line(document, page, bottom.color(), bottom.width(), x, y, x + width, y, bottom.dash(), bottom.gap());
        }
        if (left != null && left.visible()) {
            line(document, page, left.color(), left.width(), x, y, x, y + height, left.dash(), left.gap());
        }
    }

    public static void beginClip(PdfDocument document, PdfPage page, float x, float y, float width, float height) {
        document.ensureOpen();
        var operators = new StringBuilder("q\n");
        appendRectPath(operators, x, y, width, height, 0);
        page.append(operators.append("W n\n").toString());
    }

    public static void endGraphicsState(PdfDocument document, PdfPage page) {
        document.ensureOpen();
        page.append("Q\n");
    }

    public static void image(PdfDocument document, PdfPage page, ImageSource source, float x, float y,
                             float width, float height) {
        source.drawOn(document, page, x, y, width, height);
    }

    public static void uriLink(PdfPage page, float x, float y, float width, float height, String uri) {
        link(page, x, y, width, height, uri, 0);
    }

    public static void link(PdfPage page, float x, float y, float width, float height,
                            String uri, int destinationPage) {
        link(page, x, y, width, height, uri, destinationPage, null);
    }

    public static void link(PdfPage page, float x, float y, float width, float height,
                            String uri, int destinationPage, String namedDestination) {
        if (width <= 0 || height <= 0) {
            return;
        }
        if (uri != null && !uri.isBlank()) {
            page.addUriLink(new Rectangle(x, y, width, height), uri);
        } else if (namedDestination != null && !namedDestination.isBlank()) {
            page.addNamedGoToLink(new Rectangle(x, y, width, height), namedDestination);
        } else if (destinationPage > 0) {
            page.addGoToLink(new Rectangle(x, y, width, height), destinationPage);
        }
    }

    private static boolean sameStroke(Border top, Border right, Border bottom, Border left) {
        return top != null && top.equalsStroke(right) && top.equalsStroke(bottom) && top.equalsStroke(left);
    }

    private static void opacity(StringBuilder operators, PdfPage page, float opacity) {
        if (opacity < 0 || opacity > 1 || !Float.isFinite(opacity)) {
            throw new IllegalArgumentException("Opacity must be finite and between 0 and 1");
        }
        if (opacity < 1f) {
            operators.append('/').append(page.registerOpacity(opacity)).append(" gs\n");
        }
    }

    private static StringBuilder rgb(StringBuilder operators, Color color) {
        PdfNumbers.append(operators, color.red());
        operators.append(' ');
        PdfNumbers.append(operators, color.green());
        operators.append(' ');
        PdfNumbers.append(operators, color.blue());
        return operators;
    }

    private static void appendRectPath(StringBuilder operators, float x, float y, float width, float height,
                                       float radius) {
        var corner = Math.min(Math.max(0f, radius), Math.min(width, height) / 2f);
        if (corner <= 0) {
            PdfNumbers.append(operators, x);
            operators.append(' ');
            PdfNumbers.append(operators, y);
            operators.append(' ');
            PdfNumbers.append(operators, width);
            operators.append(' ');
            PdfNumbers.append(operators, height);
            operators.append(" re\n");
            return;
        }
        var right = x + width;
        var top = y + height;
        var kappa = corner * CORNER_KAPPA;
        move(operators, x + corner, y);
        lineTo(operators, right - corner, y);
        curve(operators, right - corner + kappa, y, right, y + corner - kappa, right, y + corner);
        lineTo(operators, right, top - corner);
        curve(operators, right, top - corner + kappa, right - corner + kappa, top, right - corner, top);
        lineTo(operators, x + corner, top);
        curve(operators, x + corner - kappa, top, x, top - corner + kappa, x, top - corner);
        lineTo(operators, x, y + corner);
        curve(operators, x, y + corner - kappa, x + corner - kappa, y, x + corner, y);
        operators.append("h\n");
    }

    private static void move(StringBuilder operators, float x, float y) {
        PdfNumbers.append(operators, x);
        operators.append(' ');
        PdfNumbers.append(operators, y);
        operators.append(" m\n");
    }

    private static void lineTo(StringBuilder operators, float x, float y) {
        PdfNumbers.append(operators, x);
        operators.append(' ');
        PdfNumbers.append(operators, y);
        operators.append(" l\n");
    }

    private static void curve(StringBuilder operators, float x1, float y1, float x2, float y2, float x3, float y3) {
        PdfNumbers.append(operators, x1);
        operators.append(' ');
        PdfNumbers.append(operators, y1);
        operators.append(' ');
        PdfNumbers.append(operators, x2);
        operators.append(' ');
        PdfNumbers.append(operators, y2);
        operators.append(' ');
        PdfNumbers.append(operators, x3);
        operators.append(' ');
        PdfNumbers.append(operators, y3);
        operators.append(" c\n");
    }
}
