package org.skaldpdf.barcode;

import org.skaldpdf.font.PdfFontFactory;
import org.skaldpdf.image.ImageSource;
import org.skaldpdf.pdf.PdfDocument;
import org.skaldpdf.pdf.PdfNumbers;
import org.skaldpdf.pdf.PdfPage;

import java.util.Objects;

/** An immutable, validated EAN-13 symbol with human-readable text. */
public final class Ean13Barcode implements ImageSource {
    private static final int LEFT_QUIET_MODULES = 11;
    private static final int RIGHT_QUIET_MODULES = 7;
    private static final String[] LEFT_ODD = {
        "0001101", "0011001", "0010011", "0111101", "0100011",
        "0110001", "0101111", "0111011", "0110111", "0001011"
    };
    private static final String[] LEFT_EVEN = {
        "0100111", "0110011", "0011011", "0100001", "0011101",
        "0111001", "0000101", "0010001", "0001001", "0010111"
    };
    private static final String[] RIGHT = {
        "1110010", "1100110", "1101100", "1000010", "1011100",
        "1001110", "1010000", "1000100", "1001000", "1110100"
    };
    private static final String[] PARITY = {
        "LLLLLL", "LLGLGG", "LLGGLG", "LLGGGL", "LGLLGG",
        "LGGLLG", "LGGGLL", "LGLGLG", "LGLGGL", "LGGLGL"
    };

    private final String value;
    private final byte[] modules;
    private final float moduleWidth;
    private final float barHeight;
    private final float fontSize;

    public Ean13Barcode(String value) {
        this(value, 0.8f, 24f, 8f);
    }

    private Ean13Barcode(String value, float moduleWidth, float barHeight, float fontSize) {
        this.value = normalize(value);
        this.modules = encode(this.value);
        this.moduleWidth = positive(moduleWidth, "moduleWidth");
        this.barHeight = positive(barHeight, "barHeight");
        this.fontSize = positive(fontSize, "fontSize");
    }

    public String value() {
        return value;
    }

    public float moduleWidth() {
        return moduleWidth;
    }

    public float barHeight() {
        return barHeight;
    }

    public float fontSize() {
        return fontSize;
    }

    public byte[] encodedModules() {
        return modules.clone();
    }

    public Ean13Barcode withModuleWidth(float value) {
        return new Ean13Barcode(this.value, value, barHeight, fontSize);
    }

    public Ean13Barcode withBarHeight(float value) {
        return new Ean13Barcode(this.value, moduleWidth, value, fontSize);
    }

    public Ean13Barcode withFontSize(float value) {
        return new Ean13Barcode(this.value, moduleWidth, barHeight, value);
    }

    /** Computes the check digit for exactly twelve EAN-13 payload digits. */
    public static int checkDigit(String payload) {
        Objects.requireNonNull(payload, "payload");
        if (!payload.matches("\\d{12}")) {
            throw new IllegalArgumentException("An EAN-13 payload must contain exactly 12 digits");
        }
        var total = 0;
        for (int index = 0; index < payload.length(); index++) {
            var digit = payload.charAt(index) - '0';
            total += digit * (index % 2 == 0 ? 1 : 3);
        }
        return (10 - total % 10) % 10;
    }

    @Override
    public float intrinsicWidth() {
        return (LEFT_QUIET_MODULES + modules.length + RIGHT_QUIET_MODULES) * moduleWidth;
    }

    @Override
    public float intrinsicHeight() {
        return barHeight + fontSize + 2f;
    }

    @Override
    public void drawOn(PdfDocument document, PdfPage page, float x, float y, float width, float height) {
        Objects.requireNonNull(document, "document").ensureOpen();
        Objects.requireNonNull(page, "page");
        positive(width, "width");
        positive(height, "height");
        var scaleX = width / intrinsicWidth();
        var scaleY = height / intrinsicHeight();
        var textHeight = fontSize * scaleY + 2f;
        var barBottom = y + textHeight;
        var scaledModuleWidth = moduleWidth * scaleX;
        var barsX = x + LEFT_QUIET_MODULES * scaledModuleWidth;
        var operators = new StringBuilder("q\n0 0 0 rg\n");
        var runStart = -1;
        for (int index = 0; index <= modules.length; index++) {
            var black = index < modules.length && modules[index] == 1;
            if (black && runStart < 0) {
                runStart = index;
            } else if (!black && runStart >= 0) {
                operators.append(number(barsX + runStart * scaledModuleWidth)).append(' ')
                    .append(number(barBottom)).append(' ')
                    .append(number((index - runStart) * scaledModuleWidth)).append(' ')
                    .append(number(barHeight * scaleY)).append(" re\n");
                runStart = -1;
            }
        }
        page.append(operators.append("f\nQ\n").toString());
        drawLabel(page, x, y + 1f, width, fontSize * scaleY);
    }

    private void drawLabel(PdfPage page, float x, float baseline, float width, float scaledFontSize) {
        var font = PdfFontFactory.regular();
        var run = font.glyphRun(value);
        var textWidth = run.advance() * scaledFontSize / 1_000f;
        var fontName = page.registerFont(font, run);
        var operators = new StringBuilder(96 + run.glyphs().length * 4)
            .append("q\n0 0 0 rg\nBT\n/").append(fontName).append(' ')
            .append(number(scaledFontSize)).append(" Tf\n1 0 0 1 ")
            .append(number(x + (width - textWidth) / 2f)).append(' ')
            .append(number(baseline)).append(" Tm\n<");
        for (var glyph : run.glyphs()) {
            PdfNumbers.appendHex4(operators, glyph);
        }
        page.append(operators.append("> Tj\nET\nQ\n").toString());
    }

    private static String normalize(String value) {
        Objects.requireNonNull(value, "value");
        if (!value.matches("\\d{12,13}")) {
            throw new IllegalArgumentException("EAN-13 requires 12 payload digits or 13 digits with a check digit");
        }
        var complete = value.length() == 12 ? value + checkDigit(value) : value;
        if (complete.charAt(12) - '0' != checkDigit(complete.substring(0, 12))) {
            throw new IllegalArgumentException("Invalid EAN-13 check digit");
        }
        return complete;
    }

    private static byte[] encode(String value) {
        var bits = new StringBuilder(95).append("101");
        var parity = PARITY[value.charAt(0) - '0'];
        for (int index = 1; index <= 6; index++) {
            var digit = value.charAt(index) - '0';
            bits.append(parity.charAt(index - 1) == 'L' ? LEFT_ODD[digit] : LEFT_EVEN[digit]);
        }
        bits.append("01010");
        for (int index = 7; index <= 12; index++) {
            bits.append(RIGHT[value.charAt(index) - '0']);
        }
        bits.append("101");
        var result = new byte[bits.length()];
        for (int index = 0; index < bits.length(); index++) {
            result[index] = (byte) (bits.charAt(index) - '0');
        }
        return result;
    }

    private static float positive(float value, String name) {
        if (!Float.isFinite(value) || value <= 0) {
            throw new IllegalArgumentException(name + " must be finite and positive");
        }
        return value;
    }

    private static String number(float value) {
        return PdfNumbers.format(value);
    }
}
