package org.skaldpdf.barcode;

import org.skaldpdf.fonts.SkaldSans;
import org.skaldpdf.image.ImageSource;
import org.skaldpdf.pdf.PdfDocument;
import org.skaldpdf.pdf.PdfNumbers;
import org.skaldpdf.pdf.PdfPage;

import java.util.Objects;

/** An immutable Code 128 subset B symbol with human-readable text. */
public final class Code128Barcode implements ImageSource {
    static final int QUIET_MODULES = 10;
    static final int FNC1 = 102;
    static final int CODE_B = 100;
    static final int START_B = 104;
    static final int START_C = 105;
    static final int STOP = 106;
    static final String[] PATTERNS = {
        "11011001100", "11001101100", "11001100110", "10010011000", "10010001100",
        "10001001100", "10011001000", "10011000100", "10001100100", "11001001000",
        "11001000100", "11000100100", "10110011100", "10011011100", "10011001110",
        "10111001100", "10011101100", "10011100110", "11001110010", "11001011100",
        "11001001110", "11011100100", "11001110100", "11101101110", "11101001100",
        "11100101100", "11100100110", "11101100100", "11100110100", "11100110010",
        "11011011000", "11011000110", "11000110110", "10100011000", "10001011000",
        "10001000110", "10110001000", "10001101000", "10001100010", "11010001000",
        "11000101000", "11000100010", "10110111000", "10110001110", "10001101110",
        "10111011000", "10111000110", "10001110110", "11101110110", "11010001110",
        "11000101110", "11011101000", "11011100010", "11011101110", "11101011000",
        "11101000110", "11100010110", "11101101000", "11101100010", "11100011010",
        "11101111010", "11001000010", "11110001010", "10100110000", "10100001100",
        "10010110000", "10010000110", "10000101100", "10000100110", "10110010000",
        "10110000100", "10011010000", "10011000010", "10000110100", "10000110010",
        "11000010010", "11001010000", "11110111010", "11000010100", "10001111010",
        "10100111100", "10010111100", "10010011110", "10111100100", "10011110100",
        "10011110010", "11110100100", "11110010100", "11110010010", "11011011110",
        "11011110110", "11110110110", "10101111000", "10100011110", "10001011110",
        "10111101000", "10111100010", "11110101000", "11110100010", "10111011110",
        "10111101110", "11101011110", "11110101110", "11010000100", "11010010000",
        "11010011100", "1100011101011"
    };

    private final String value;
    private final byte[] modules;
    private final float moduleWidth;
    private final float barHeight;
    private final float fontSize;

    public Code128Barcode(String value) {
        this(value, 0.8f, 28f, 8f);
    }

    private Code128Barcode(String value, float moduleWidth, float barHeight, float fontSize) {
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

    public Code128Barcode withModuleWidth(float value) {
        return new Code128Barcode(this.value, value, barHeight, fontSize);
    }

    public Code128Barcode withBarHeight(float value) {
        return new Code128Barcode(this.value, moduleWidth, value, fontSize);
    }

    public Code128Barcode withFontSize(float value) {
        return new Code128Barcode(this.value, moduleWidth, barHeight, value);
    }

    @Override
    public float intrinsicWidth() {
        return (QUIET_MODULES * 2 + modules.length) * moduleWidth;
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
        var barsX = x + QUIET_MODULES * scaledModuleWidth;
        var operators = new StringBuilder("q\n0 0 0 rg\n");
        var runStart = -1;
        for (int index = 0; index <= modules.length; index++) {
            var black = index < modules.length && modules[index] == 1;
            if (black && runStart < 0) {
                runStart = index;
            } else if (!black && runStart >= 0) {
                PdfNumbers.append(operators, barsX + runStart * scaledModuleWidth);
                operators.append(' ');
                PdfNumbers.append(operators, barBottom);
                operators.append(' ');
                PdfNumbers.append(operators, (index - runStart) * scaledModuleWidth);
                operators.append(' ');
                PdfNumbers.append(operators, barHeight * scaleY);
                operators.append(" re\n");
                runStart = -1;
            }
        }
        page.append(operators.append("f\nQ\n").toString());
        drawLabel(page, x, y + 1f, width, fontSize * scaleY);
    }

    private void drawLabel(PdfPage page, float x, float baseline, float width, float scaledFontSize) {
        var font = SkaldSans.regular();
        var run = font.glyphRun(value);
        var textWidth = run.advance() * scaledFontSize / 1_000f;
        var fontName = page.registerFont(font, run);
        var operators = new StringBuilder(96 + run.glyphs().length * 4)
            .append("q\n0 0 0 rg\nBT\n/").append(fontName).append(' ');
        PdfNumbers.append(operators, scaledFontSize);
        operators.append(" Tf\n1 0 0 1 ");
        PdfNumbers.append(operators, x + (width - textWidth) / 2f);
        operators.append(' ');
        PdfNumbers.append(operators, baseline);
        operators.append(" Tm\n<");
        for (var glyph : run.glyphs()) {
            PdfNumbers.appendHex4(operators, glyph);
        }
        page.append(operators.append("> Tj\nET\nQ\n").toString());
    }

    private static String normalize(String value) {
        Objects.requireNonNull(value, "value");
        if (value.isEmpty() || value.length() > 80) {
            throw new IllegalArgumentException("Code 128 text must contain 1 to 80 characters");
        }
        for (int index = 0; index < value.length(); index++) {
            var character = value.charAt(index);
            if (character < 32 || character > 126) {
                throw new IllegalArgumentException("Code 128 subset B supports ASCII 32-126");
            }
        }
        return value;
    }

    private static byte[] encode(String value) {
        var symbols = new int[value.length() + 3];
        symbols[0] = START_B;
        var checksum = START_B;
        for (int index = 0; index < value.length(); index++) {
            var symbol = value.charAt(index) - 32;
            symbols[index + 1] = symbol;
            checksum += symbol * (index + 1);
        }
        symbols[symbols.length - 2] = checksum % 103;
        symbols[symbols.length - 1] = STOP;
        return modulesFor(symbols);
    }

    static byte[] modulesFor(int[] symbols) {
        var bits = new StringBuilder(symbols.length * 11 + 2);
        for (var symbol : symbols) {
            bits.append(PATTERNS[symbol]);
        }
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
}
