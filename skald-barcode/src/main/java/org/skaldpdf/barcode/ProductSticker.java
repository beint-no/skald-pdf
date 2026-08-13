package org.skaldpdf.barcode;

import org.skaldpdf.font.PdfFont;
import org.skaldpdf.font.PdfFontFactory;
import org.skaldpdf.geom.PageSize;
import org.skaldpdf.pdf.PdfDocument;
import org.skaldpdf.pdf.PdfNumbers;
import org.skaldpdf.pdf.PdfPage;
import org.skaldpdf.pdf.PdfWriter;

import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Clothing / product EAN-13 sticker used by ecomtools.
 * Page is 93 mm × 35 mm: barcode on the left, origin/SKU/size on the right,
 * product title and composition underneath.
 */
public final class ProductSticker {
    public static final float PAGE_WIDTH_MM = 93f;
    public static final float PAGE_HEIGHT_MM = 35f;
    public static final PageSize PAGE_SIZE = new PageSize(millimetres(PAGE_WIDTH_MM), millimetres(PAGE_HEIGHT_MM));

    private static final float MM = 72f / 25.4f;
    private static final float FONT_SIZE = 7f;
    private static final float BAR_HEIGHT = 41f;
    private static final float MODULE_WIDTH = 1.35f;
    private static final float LEFT_MARGIN = 10f;
    private static final float COLUMN_TWO_X = 160f;
    private static final float LINE_GAP = 12f;
    private static final float HORIZONTAL_PADDING = 20f;
    private static final float COMPOSITION_MIN_FONT = 4f;
    private static final float COMPOSITION_LINE_GAP = 1.5f;

    private ProductSticker() {
    }

    public record Spec(
        String sku,
        String countryOfOrigin,
        String title,
        String size,
        String length,
        String composition,
        String ean13,
        String color
    ) {
        public Spec {
            sku = nonBlank(sku, "sku");
            countryOfOrigin = Objects.requireNonNullElse(countryOfOrigin, "");
            title = Objects.requireNonNullElse(title, "");
            size = Objects.requireNonNullElse(size, "");
            length = Objects.requireNonNullElse(length, "");
            composition = Objects.requireNonNullElse(composition, "");
            ean13 = nonBlank(ean13, "ean13");
            color = Objects.requireNonNullElse(color, "");
        }

        public String displayTitle() {
            if (color.isBlank()) {
                return title;
            }
            if (title.isBlank()) {
                return color;
            }
            return color + " " + title;
        }
    }

    public static byte[] pdf(Spec spec) {
        Objects.requireNonNull(spec, "spec");
        var output = new ByteArrayOutputStream();
        try (var document = new PdfDocument(new PdfWriter(output))) {
            draw(document.addNewPage(PAGE_SIZE), spec);
        }
        return output.toByteArray();
    }

    public static void write(Path path, Spec spec) {
        Objects.requireNonNull(spec, "spec");
        try (var document = new PdfDocument(new PdfWriter(path))) {
            draw(document.addNewPage(PAGE_SIZE), spec);
        }
    }

    public static String fileName(Spec spec) {
        return spec.sku() + "_" + new Ean13Barcode(spec.ean13()).value() + "_ean_sticker.pdf";
    }

    private static void draw(PdfPage page, Spec spec) {
        var document = page.document();
        var font = PdfFontFactory.bold();
        var barcode = new Ean13Barcode(spec.ean13())
            .withModuleWidth(MODULE_WIDTH)
            .withBarHeight(BAR_HEIGHT)
            .withFontSize(FONT_SIZE)
            .withQuietZones(8, 7);
        var maxWidth = PAGE_SIZE.getWidth() * 0.58f;
        var maxHeight = PAGE_SIZE.getHeight() / 2f;
        var scale = Math.min(1f, Math.min(maxWidth / barcode.intrinsicWidth(), maxHeight / barcode.intrinsicHeight()));
        var barcodeWidth = barcode.intrinsicWidth() * scale;
        var barcodeHeight = barcode.intrinsicHeight() * scale;
        var barcodeBottom = PAGE_SIZE.getHeight() * 0.43f;
        barcode.drawOn(document, page, LEFT_MARGIN, barcodeBottom, barcodeWidth, barcodeHeight);

        var metaTop = PAGE_SIZE.getHeight() / 1.3f;
        drawLine(page, font, "Made in: " + spec.countryOfOrigin(), COLUMN_TWO_X, metaTop);
        drawLine(page, font, "SKU: " + spec.sku(), COLUMN_TWO_X, metaTop - LINE_GAP);
        drawLine(page, font, "Size: " + spec.size(), COLUMN_TWO_X, metaTop - 2 * LINE_GAP);
        if (!spec.length().isBlank()) {
            drawLine(page, font, "Length: " + spec.length(), COLUMN_TWO_X, metaTop - 3 * LINE_GAP);
        }

        var titleBottom = barcodeBottom - LINE_GAP;
        drawLine(page, font, spec.displayTitle(), LEFT_MARGIN, titleBottom);

        var compositionTop = titleBottom - LINE_GAP;
        var maxCompositionWidth = PAGE_SIZE.getWidth() - HORIZONTAL_PADDING;
        var maxCompositionHeight = compositionTop - 3f;
        var wrapped = wrapComposition(spec.composition(), font, maxCompositionWidth, maxCompositionHeight);
        var lineY = compositionTop;
        for (var line : wrapped.lines()) {
            drawText(page, font, line, LEFT_MARGIN, lineY - wrapped.fontSize(), wrapped.fontSize());
            lineY -= wrapped.fontSize() + COMPOSITION_LINE_GAP;
        }
    }

    private static void drawLine(PdfPage page, PdfFont font, String text, float x, float top) {
        drawText(page, font, text, x, top - FONT_SIZE, FONT_SIZE);
    }

    private static void drawText(PdfPage page, PdfFont font, String text, float x, float baseline, float fontSize) {
        if (text == null || text.isBlank()) {
            return;
        }
        var run = font.glyphRun(text);
        var fontName = page.registerFont(font, run);
        var operators = new StringBuilder(80 + run.glyphs().length * 4)
            .append("0 0 0 rg\nBT\n/").append(fontName).append(' ');
        PdfNumbers.append(operators, fontSize);
        operators.append(" Tf\n1 0 0 1 ");
        PdfNumbers.append(operators, x);
        operators.append(' ');
        PdfNumbers.append(operators, baseline);
        operators.append(" Tm\n<");
        for (var glyph : run.glyphs()) {
            PdfNumbers.appendHex4(operators, glyph);
        }
        page.append(operators.append("> Tj\nET\n").toString());
    }

    private static WrappedComposition wrapComposition(String composition, PdfFont font,
                                                      float maxWidth, float maxHeight) {
        var source = composition == null ? "" : composition.strip();
        if (source.isEmpty()) {
            return new WrappedComposition(FONT_SIZE, List.of());
        }
        var prefixed = source.regionMatches(true, 0, "Composition:", 0, "Composition:".length())
            ? source
            : "Composition: " + source;
        var words = prefixed.split("\\s+");
        var fontSize = FONT_SIZE;
        while (fontSize >= COMPOSITION_MIN_FONT) {
            var lines = wrapWords(words, font, fontSize, maxWidth);
            if (compositionHeight(lines.size(), fontSize) <= maxHeight) {
                return new WrappedComposition(fontSize, lines);
            }
            fontSize -= 0.5f;
        }
        var lines = wrapWords(words, font, COMPOSITION_MIN_FONT, maxWidth);
        var maxLines = Math.max(1, (int) ((maxHeight + COMPOSITION_LINE_GAP)
            / (COMPOSITION_MIN_FONT + COMPOSITION_LINE_GAP)));
        if (lines.size() <= maxLines) {
            return new WrappedComposition(COMPOSITION_MIN_FONT, lines);
        }
        var fitted = new ArrayList<>(lines.subList(0, maxLines));
        var last = fitted.getLast();
        while (!last.isEmpty() && font.getWidth(last + "...", COMPOSITION_MIN_FONT) > maxWidth) {
            last = last.substring(0, last.length() - 1).stripTrailing();
        }
        fitted.set(fitted.size() - 1, last.isEmpty() ? "..." : last + "...");
        return new WrappedComposition(COMPOSITION_MIN_FONT, List.copyOf(fitted));
    }

    private static List<String> wrapWords(String[] words, PdfFont font, float fontSize, float maxWidth) {
        var lines = new ArrayList<String>();
        var current = new StringBuilder();
        for (var word : words) {
            var candidate = current.isEmpty() ? word : current + " " + word;
            if (font.getWidth(candidate, fontSize) <= maxWidth || current.isEmpty()) {
                current.setLength(0);
                current.append(candidate);
            } else {
                lines.add(current.toString());
                current.setLength(0);
                current.append(word);
            }
        }
        if (!current.isEmpty()) {
            lines.add(current.toString());
        }
        return List.copyOf(lines);
    }

    private static float compositionHeight(int lines, float fontSize) {
        if (lines <= 0) {
            return 0f;
        }
        return fontSize + (lines - 1) * (fontSize + COMPOSITION_LINE_GAP);
    }

    private static float millimetres(float value) {
        return value * MM;
    }

    private static String nonBlank(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    private record WrappedComposition(float fontSize, List<String> lines) {
    }
}
