package org.skaldpdf.barcode;

import org.skaldpdf.fonts.SkaldSans;
import org.skaldpdf.image.ImageSource;
import org.skaldpdf.pdf.PdfDocument;
import org.skaldpdf.pdf.PdfNumbers;
import org.skaldpdf.pdf.PdfPage;

import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;

/**
 * GS1-128 (UCC/EAN-128): Code 128 with a leading FNC1 and AI-structured payload.
 */
public final class Gs1128Barcode implements ImageSource {
    private static final Map<String, Integer> FIXED_AI_LENGTHS = Map.ofEntries(
        Map.entry("00", 18), Map.entry("01", 14), Map.entry("02", 14),
        Map.entry("11", 6), Map.entry("12", 6), Map.entry("13", 6),
        Map.entry("15", 6), Map.entry("16", 6), Map.entry("17", 6),
        Map.entry("20", 2), Map.entry("414", 13), Map.entry("422", 3)
    );

    private final String elementString;
    private final String hri;
    private final byte[] modules;
    private final float moduleWidth;
    private final float barHeight;
    private final float fontSize;

    public Gs1128Barcode(String elementString) {
        this(elementString, 0.8f, 28f, 8f);
    }

    private Gs1128Barcode(String elementString, float moduleWidth, float barHeight, float fontSize) {
        var parsed = parse(Objects.requireNonNull(elementString, "elementString"));
        this.elementString = parsed.raw();
        this.hri = parsed.hri();
        this.modules = encode(parsed.payload());
        this.moduleWidth = positive(moduleWidth, "moduleWidth");
        this.barHeight = positive(barHeight, "barHeight");
        this.fontSize = positive(fontSize, "fontSize");
    }

    public Gs1128Barcode withModuleWidth(float value) {
        return new Gs1128Barcode(elementString, value, barHeight, fontSize);
    }

    public Gs1128Barcode withBarHeight(float value) {
        return new Gs1128Barcode(elementString, moduleWidth, value, fontSize);
    }

    public Gs1128Barcode withFontSize(float value) {
        return new Gs1128Barcode(elementString, moduleWidth, barHeight, value);
    }

    public String value() {
        return elementString;
    }

    public String humanReadable() {
        return hri;
    }

    public byte[] encodedModules() {
        return modules.clone();
    }

    @Override
    public float intrinsicWidth() {
        return (Code128Barcode.QUIET_MODULES * 2 + modules.length) * moduleWidth;
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
        var barsX = x + Code128Barcode.QUIET_MODULES * scaledModuleWidth;
        var operators = new StringBuilder("q\n0 0 0 rg\n");
        var runStart = -1;
        for (int index = 0; index <= modules.length; index++) {
            var black = index < modules.length && modules[index] == 1;
            if (black && runStart < 0) {
                runStart = index;
            } else if (!black && runStart >= 0) {
                operators.append(PdfNumbers.format(barsX + runStart * scaledModuleWidth)).append(' ')
                    .append(PdfNumbers.format(barBottom)).append(' ')
                    .append(PdfNumbers.format((index - runStart) * scaledModuleWidth)).append(' ')
                    .append(PdfNumbers.format(barHeight * scaleY)).append(" re\n");
                runStart = -1;
            }
        }
        page.append(operators.append("f\nQ\n").toString());
        drawLabel(page, x, y + 1f, width, fontSize * scaleY);
    }

    private void drawLabel(PdfPage page, float x, float baseline, float width, float scaledFontSize) {
        var font = SkaldSans.regular();
        var run = font.glyphRun(hri);
        var textWidth = run.advance() * scaledFontSize / 1_000f;
        var fontName = page.registerFont(font, run);
        var operators = new StringBuilder(96 + run.glyphs().length * 4)
            .append("q\n0 0 0 rg\nBT\n/").append(fontName).append(' ')
            .append(PdfNumbers.format(scaledFontSize)).append(" Tf\n1 0 0 1 ")
            .append(PdfNumbers.format(x + Math.max(0f, (width - textWidth) / 2f))).append(' ')
            .append(PdfNumbers.format(baseline)).append(" Tm\n<");
        for (var glyph : run.glyphs()) {
            PdfNumbers.appendHex4(operators, glyph);
        }
        page.append(operators.append("> Tj\nET\nQ\n").toString());
    }

    private static Parsed parse(String source) {
        var stripped = source.strip();
        if (stripped.isEmpty()) {
            throw new IllegalArgumentException("GS1-128 payload must not be empty");
        }
        if (stripped.charAt(0) != '(') {
            throw new IllegalArgumentException("GS1-128 requires an element string such as (01)09501101530003");
        }
        var elements = new ArrayList<Ai>();
        var cursor = 0;
        while (cursor < stripped.length()) {
            if (stripped.charAt(cursor) != '(') {
                throw new IllegalArgumentException("GS1-128 AIs must be written as (AI)value");
            }
            var close = stripped.indexOf(')', cursor);
            if (close < 0 || close == cursor + 1) {
                throw new IllegalArgumentException("GS1-128 AI is missing a closing parenthesis");
            }
            var ai = stripped.substring(cursor + 1, close);
            if (!ai.chars().allMatch(Character::isDigit) || ai.length() < 2 || ai.length() > 4) {
                throw new IllegalArgumentException("GS1-128 AI must be 2 to 4 digits: " + ai);
            }
            cursor = close + 1;
            var next = stripped.indexOf('(', cursor);
            var value = stripped.substring(cursor, next < 0 ? stripped.length() : next);
            if (value.isEmpty()) {
                throw new IllegalArgumentException("GS1-128 AI " + ai + " is missing a value");
            }
            var fixed = fixedLength(ai);
            if (fixed > 0 && value.length() != fixed) {
                throw new IllegalArgumentException("GS1-128 AI " + ai + " requires " + fixed + " characters");
            }
            elements.add(new Ai(ai, value, fixed == 0));
            cursor = next < 0 ? stripped.length() : next;
        }
        var payload = new StringBuilder();
        var hri = new StringBuilder();
        for (int index = 0; index < elements.size(); index++) {
            var element = elements.get(index);
            hri.append('(').append(element.ai()).append(')').append(element.value());
            payload.append(element.value());
            if (element.variable() && index + 1 < elements.size()) {
                payload.append('\u001D');
            }
        }
        return new Parsed(stripped, hri.toString(), payload.toString());
    }

    private static int fixedLength(String ai) {
        var exact = FIXED_AI_LENGTHS.get(ai);
        if (exact != null) {
            return exact;
        }
        if (ai.length() == 4 && (ai.startsWith("310") || ai.startsWith("311") || ai.startsWith("312")
            || ai.startsWith("313") || ai.startsWith("314") || ai.startsWith("315") || ai.startsWith("316")
            || ai.startsWith("320") || ai.startsWith("330") || ai.startsWith("340") || ai.startsWith("350")
            || ai.startsWith("351") || ai.startsWith("352") || ai.startsWith("356") || ai.startsWith("360"))) {
            return 6;
        }
        return 0;
    }

    private static byte[] encode(String payload) {
        var symbols = new ArrayList<Integer>();
        symbols.add(Code128Barcode.START_C);
        var checksum = Code128Barcode.START_C;
        var weight = 1;
        symbols.add(Code128Barcode.FNC1);
        checksum += Code128Barcode.FNC1 * weight++;
        var index = 0;
        var subsetC = true;
        while (index < payload.length()) {
            if (payload.charAt(index) == '\u001D') {
                symbols.add(Code128Barcode.FNC1);
                checksum += Code128Barcode.FNC1 * weight++;
                index++;
                continue;
            }
            if (subsetC && index + 1 < payload.length() && isDigit(payload.charAt(index))
                && isDigit(payload.charAt(index + 1))) {
                var symbol = (payload.charAt(index) - '0') * 10 + (payload.charAt(index + 1) - '0');
                symbols.add(symbol);
                checksum += symbol * weight++;
                index += 2;
                continue;
            }
            if (subsetC) {
                symbols.add(Code128Barcode.CODE_B);
                checksum += Code128Barcode.CODE_B * weight++;
                subsetC = false;
            }
            var character = payload.charAt(index);
            if (character < 32 || character > 126) {
                throw new IllegalArgumentException("GS1-128 value contains an unsupported character");
            }
            var symbol = character - 32;
            symbols.add(symbol);
            checksum += symbol * weight++;
            index++;
        }
        symbols.add(checksum % 103);
        symbols.add(Code128Barcode.STOP);
        return Code128Barcode.modulesFor(symbols.stream().mapToInt(Integer::intValue).toArray());
    }

    private static boolean isDigit(char value) {
        return value >= '0' && value <= '9';
    }

    private static float positive(float value, String name) {
        if (!Float.isFinite(value) || value <= 0) {
            throw new IllegalArgumentException(name + " must be finite and positive");
        }
        return value;
    }

    private record Ai(String ai, String value, boolean variable) {
    }

    private record Parsed(String raw, String hri, String payload) {
    }
}
