package org.skaldpdf.pdf;

import static org.skaldpdf.pdf.CosValue.CosDictionary;
import static org.skaldpdf.pdf.CosValue.CosName;
import static org.skaldpdf.pdf.CosValue.CosStream;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Best-effort text extraction from imported pages. Glyphs are mapped through
 * each font's ToUnicode CMap when present; otherwise WinAnsi / PDFDocEncoding
 * is used for single-byte strings. This is for invoices, slips, and similar
 * business files — not a full tagged-PDF reader.
 */
public final class PdfText {
    private static final Pattern CODE_SPACE = Pattern.compile(
        "begincodespacerange\\s*<([0-9A-Fa-f]+)>\\s*<([0-9A-Fa-f]+)>", Pattern.DOTALL);
    private static final Pattern HEX_PAIR = Pattern.compile("<([0-9A-Fa-f]+)>\\s*<([0-9A-Fa-f]+)>");
    private static final Pattern RANGE_DEST = Pattern.compile(
        "<([0-9A-Fa-f]+)>\\s*<([0-9A-Fa-f]+)>\\s*<([0-9A-Fa-f]+)>");
    private static final Pattern RANGE_ARRAY = Pattern.compile(
        "<([0-9A-Fa-f]+)>\\s*<([0-9A-Fa-f]+)>\\s*\\[(.*?)]", Pattern.DOTALL);
    private static final Pattern HEX_TOKEN = Pattern.compile("<([0-9A-Fa-f]+)>");

    private PdfText() {
    }

    public static String extract(byte[] pdf) {
        return String.join("\n", extractPages(pdf));
    }

    public static List<String> extractPages(byte[] pdf) {
        Objects.requireNonNull(pdf, "pdf");
        var parser = new NativePdfParser(pdf);
        var pages = parser.pages();
        var result = new ArrayList<String>(pages.size());
        for (var page : pages) {
            result.add(extractPage(parser, page));
        }
        return List.copyOf(result);
    }

    private static String extractPage(NativePdfParser parser, ImportedPage page) {
        var fonts = loadFonts(parser, page);
        var scanner = new Scanner(parser.contentBytes(page));
        var operands = new ArrayList<Object>();
        var text = new StringBuilder();
        var lastY = Float.NaN;
        String fontName = null;
        while (true) {
            var token = scanner.next();
            if (token == null) {
                break;
            }
            if (token instanceof Operator operator) {
                switch (operator.name()) {
                    case "Tf" -> {
                        if (operands.size() >= 2 && operands.get(operands.size() - 2) instanceof String name) {
                            fontName = name;
                        }
                    }
                    case "Tm" -> {
                        if (operands.size() >= 6 && operands.get(operands.size() - 1) instanceof Number y) {
                            lastY = breakLine(text, lastY, y.floatValue());
                        }
                    }
                    case "Td", "TD" -> {
                        if (!operands.isEmpty() && operands.get(operands.size() - 1) instanceof Number y
                            && Math.abs(y.floatValue()) > 1f) {
                            lastY = breakLine(text, lastY, Float.isNaN(lastY) ? 0 : lastY - y.floatValue());
                        }
                    }
                    case "Tj", "'" -> appendShow(text, fonts, fontName, lastOperand(operands));
                    case "\"" -> appendShow(text, fonts, fontName, lastOperand(operands));
                    case "TJ" -> {
                        if (lastOperand(operands) instanceof List<?> array) {
                            for (var item : array) {
                                appendShow(text, fonts, fontName, item);
                            }
                        }
                    }
                    case "BI" -> scanner.skipInlineImage();
                    default -> {
                    }
                }
                operands.clear();
            } else {
                operands.add(token);
            }
        }
        return collapse(text.toString());
    }

    private static float breakLine(StringBuilder text, float lastY, float y) {
        if (!text.isEmpty() && text.charAt(text.length() - 1) != '\n') {
            if (!Float.isNaN(lastY) && Math.abs(y - lastY) > 2f) {
                text.append('\n');
            } else {
                text.append(' ');
            }
        }
        return y;
    }

    private static Object lastOperand(List<Object> operands) {
        return operands.isEmpty() ? null : operands.get(operands.size() - 1);
    }

    private static void appendShow(StringBuilder text, Map<String, CidMap> fonts, String fontName, Object operand) {
        if (!(operand instanceof byte[] bytes) || bytes.length == 0) {
            return;
        }
        var mapped = fonts.getOrDefault(fontName, CidMap.WIN_ANSI).map(bytes);
        if (mapped.isEmpty()) {
            return;
        }
        if (!text.isEmpty()) {
            var last = text.charAt(text.length() - 1);
            if (last != ' ' && last != '\n' && !Character.isWhitespace(mapped.charAt(0))) {
                text.append(' ');
            }
        }
        text.append(mapped);
    }

    private static Map<String, CidMap> loadFonts(NativePdfParser parser, ImportedPage page) {
        var resourcesValue = page.dictionary().get("Resources");
        if (resourcesValue == null) {
            return Map.of();
        }
        var resolved = parser.resolve(resourcesValue);
        if (!(resolved instanceof CosDictionary resources)) {
            return Map.of();
        }
        var fontsValue = resources.get("Font");
        if (fontsValue == null) {
            return Map.of();
        }
        var fonts = parser.resolve(fontsValue);
        if (!(fonts instanceof CosDictionary dictionary)) {
            return Map.of();
        }
        var result = new HashMap<String, CidMap>();
        dictionary.values().forEach((name, value) -> {
            var font = parser.resolve(value);
            if (font instanceof CosDictionary fontDictionary) {
                result.put(name, cidMap(parser, fontDictionary));
            }
        });
        return result;
    }

    private static CidMap cidMap(NativePdfParser parser, CosDictionary font) {
        var toUnicode = font.get("ToUnicode");
        if (toUnicode != null && parser.resolve(toUnicode) instanceof CosStream stream) {
            try {
                return CidMap.parse(parser.decodedStream(stream, "ToUnicode"));
            } catch (RuntimeException ignored) {
                // Fall through to the encoding name.
            }
        }
        var encodingValue = font.get("Encoding") == null ? null : parser.resolve(font.get("Encoding"));
        if (encodingValue instanceof CosName encoding) {
            return CidMap.named(encoding.value());
        }
        if (encodingValue instanceof CosDictionary encoding
            && encoding.get("BaseEncoding") instanceof CosName base) {
            return CidMap.named(base.value());
        }
        return CidMap.WIN_ANSI;
    }

    private static String collapse(String value) {
        return value.replaceAll("[\\t\\x0b\\f]+", " ")
            .replaceAll(" *\\n *", "\n")
            .replaceAll(" {2,}", " ")
            .strip();
    }

    private record Operator(String name) {
    }

    private static final class CidMap {
        private static final CidMap WIN_ANSI = new CidMap(1, Map.of(), true);
        private final int codeBytes;
        private final Map<Integer, String> map;
        private final boolean singleByteFallback;

        private CidMap(int codeBytes, Map<Integer, String> map, boolean singleByteFallback) {
            this.codeBytes = Math.max(1, codeBytes);
            this.map = map;
            this.singleByteFallback = singleByteFallback;
        }

        static CidMap named(String encoding) {
            return switch (encoding) {
                case "WinAnsiEncoding", "MacRomanEncoding", "StandardEncoding", "PDFDocEncoding" -> WIN_ANSI;
                default -> WIN_ANSI;
            };
        }

        static CidMap parse(byte[] cmap) {
            var text = new String(cmap, StandardCharsets.ISO_8859_1);
            var codeBytes = 2;
            var space = CODE_SPACE.matcher(text);
            if (space.find()) {
                codeBytes = Math.max(1, space.group(1).length() / 2);
            }
            var mapped = new HashMap<Integer, String>();
            extractBlocks(text, "beginbfchar", "endbfchar").forEach(block -> {
                var pair = HEX_PAIR.matcher(block);
                while (pair.find()) {
                    mapped.put(parseHex(pair.group(1)), utf16(pair.group(2)));
                }
            });
            extractBlocks(text, "beginbfrange", "endbfrange").forEach(block -> {
                var array = RANGE_ARRAY.matcher(block);
                while (array.find()) {
                    var start = parseHex(array.group(1));
                    var dests = HEX_TOKEN.matcher(array.group(3));
                    var offset = 0;
                    while (dests.find()) {
                        mapped.put(start + offset, utf16(dests.group(1)));
                        offset++;
                    }
                }
                var dest = RANGE_DEST.matcher(block);
                while (dest.find()) {
                    if (block.indexOf('[', dest.start()) >= 0 && block.indexOf('[', dest.start()) < dest.end()) {
                        continue;
                    }
                    var start = parseHex(dest.group(1));
                    var end = parseHex(dest.group(2));
                    var first = utf16(dest.group(3));
                    var seed = first.isEmpty() ? 0 : first.codePointAt(0);
                    for (int cid = start; cid <= end; cid++) {
                        mapped.put(cid, new String(Character.toChars(seed + (cid - start))));
                    }
                }
            });
            return new CidMap(codeBytes, Map.copyOf(mapped), mapped.isEmpty());
        }

        String map(byte[] bytes) {
            var result = new StringBuilder(bytes.length);
            if (singleByteFallback && map.isEmpty()) {
                for (var item : bytes) {
                    result.append(winAnsi(item & 0xff));
                }
                return result.toString();
            }
            var index = 0;
            while (index + codeBytes <= bytes.length) {
                var cid = 0;
                for (int offset = 0; offset < codeBytes; offset++) {
                    cid = (cid << 8) | (bytes[index + offset] & 0xff);
                }
                var mapped = map.get(cid);
                if (mapped != null) {
                    result.append(mapped);
                } else if (singleByteFallback && codeBytes == 1) {
                    result.append(winAnsi(cid));
                }
                index += codeBytes;
            }
            return result.toString();
        }

        private static List<String> extractBlocks(String text, String start, String end) {
            var result = new ArrayList<String>();
            var from = 0;
            while (true) {
                var begin = text.indexOf(start, from);
                if (begin < 0) {
                    return result;
                }
                var stop = text.indexOf(end, begin + start.length());
                if (stop < 0) {
                    return result;
                }
                result.add(text.substring(begin + start.length(), stop));
                from = stop + end.length();
            }
        }

        private static int parseHex(String hex) {
            return Integer.parseUnsignedInt(hex, 16);
        }

        private static String utf16(String hex) {
            var bytes = hexToBytes(hex);
            return new String(bytes, StandardCharsets.UTF_16BE);
        }

        private static byte[] hexToBytes(String hex) {
            var normalized = hex.length() % 2 == 0 ? hex : "0" + hex;
            var bytes = new byte[normalized.length() / 2];
            for (int index = 0; index < bytes.length; index++) {
                bytes[index] = (byte) Integer.parseInt(normalized.substring(index * 2, index * 2 + 2), 16);
            }
            return bytes;
        }

        private static char winAnsi(int value) {
            return switch (value) {
                case 0x80 -> '€';
                case 0x82 -> '\u201A';
                case 0x83 -> '\u0192';
                case 0x84 -> '\u201E';
                case 0x85 -> '\u2026';
                case 0x86 -> '\u2020';
                case 0x87 -> '\u2021';
                case 0x88 -> '\u02C6';
                case 0x89 -> '\u2030';
                case 0x8A -> '\u0160';
                case 0x8B -> '\u2039';
                case 0x8C -> '\u0152';
                case 0x8E -> '\u017D';
                case 0x91 -> '\u2018';
                case 0x92 -> '\u2019';
                case 0x93 -> '\u201C';
                case 0x94 -> '\u201D';
                case 0x95 -> '\u2022';
                case 0x96 -> '\u2013';
                case 0x97 -> '\u2014';
                case 0x98 -> '\u02DC';
                case 0x99 -> '\u2122';
                case 0x9A -> '\u0161';
                case 0x9B -> '\u203A';
                case 0x9C -> '\u0153';
                case 0x9E -> '\u017E';
                case 0x9F -> '\u0178';
                default -> (char) value;
            };
        }
    }

    private static final class Scanner {
        private final byte[] bytes;
        private int position;

        Scanner(byte[] bytes) {
            this.bytes = bytes;
        }

        Object next() {
            skipIgnorable();
            if (position >= bytes.length) {
                return null;
            }
            var current = bytes[position] & 0xff;
            return switch (current) {
                case '(' -> literal();
                case '<' -> bytes[position + 1 == bytes.length ? position : position + 1] == (byte) '<'
                    ? skipDictionary() : hex();
                case '[' -> array();
                case '/' -> name();
                case ']' -> {
                    position++;
                    yield next();
                }
                default -> wordOrNumber();
            };
        }

        void skipInlineImage() {
            while (position < bytes.length) {
                skipIgnorable();
                if (position + 1 < bytes.length && bytes[position] == 'E' && bytes[position + 1] == 'I'
                    && (position + 2 >= bytes.length || isSpace(bytes[position + 2] & 0xff))) {
                    position += 2;
                    return;
                }
                position++;
            }
        }

        private void skipIgnorable() {
            while (position < bytes.length) {
                var value = bytes[position] & 0xff;
                if (value == '%') {
                    while (position < bytes.length && bytes[position] != '\n' && bytes[position] != '\r') {
                        position++;
                    }
                    continue;
                }
                if (!isSpace(value)) {
                    return;
                }
                position++;
            }
        }

        private byte[] literal() {
            position++;
            var output = new java.io.ByteArrayOutputStream();
            var depth = 1;
            while (position < bytes.length && depth > 0) {
                var value = bytes[position++] & 0xff;
                if (value == '\\') {
                    if (position >= bytes.length) {
                        break;
                    }
                    var escape = bytes[position++] & 0xff;
                    if (escape == '\n' || escape == '\r') {
                        if (escape == '\r' && position < bytes.length && bytes[position] == '\n') {
                            position++;
                        }
                        continue;
                    }
                    output.write(switch (escape) {
                        case 'n' -> '\n';
                        case 'r' -> '\r';
                        case 't' -> '\t';
                        case 'b' -> '\b';
                        case 'f' -> '\f';
                        case '(', ')', '\\' -> escape;
                        default -> {
                            if (escape >= '0' && escape <= '7') {
                                var octal = escape - '0';
                                for (int count = 1; count < 3 && position < bytes.length; count++) {
                                    var digit = bytes[position] & 0xff;
                                    if (digit < '0' || digit > '7') {
                                        break;
                                    }
                                    octal = (octal << 3) + (digit - '0');
                                    position++;
                                }
                                yield octal;
                            }
                            yield escape;
                        }
                    });
                } else if (value == '(') {
                    depth++;
                    output.write(value);
                } else if (value == ')') {
                    depth--;
                    if (depth > 0) {
                        output.write(value);
                    }
                } else {
                    output.write(value);
                }
            }
            return output.toByteArray();
        }

        private byte[] hex() {
            position++;
            var output = new java.io.ByteArrayOutputStream();
            var high = -1;
            while (position < bytes.length) {
                var value = bytes[position++] & 0xff;
                if (value == '>') {
                    break;
                }
                if (isSpace(value)) {
                    continue;
                }
                var nibble = Character.digit((char) value, 16);
                if (nibble < 0) {
                    continue;
                }
                if (high < 0) {
                    high = nibble;
                } else {
                    output.write((high << 4) | nibble);
                    high = -1;
                }
            }
            if (high >= 0) {
                output.write(high << 4);
            }
            return output.toByteArray();
        }

        private List<Object> array() {
            position++;
            var values = new ArrayList<Object>();
            while (position < bytes.length) {
                skipIgnorable();
                if (position < bytes.length && bytes[position] == ']') {
                    position++;
                    return values;
                }
                var item = next();
                if (item == null) {
                    return values;
                }
                values.add(item);
            }
            return values;
        }

        private Object skipDictionary() {
            position += 2;
            var depth = 1;
            while (position < bytes.length && depth > 0) {
                if (position + 1 < bytes.length && bytes[position] == '<' && bytes[position + 1] == '<') {
                    depth++;
                    position += 2;
                } else if (position + 1 < bytes.length && bytes[position] == '>' && bytes[position + 1] == '>') {
                    depth--;
                    position += 2;
                } else if (bytes[position] == '(') {
                    literal();
                } else {
                    position++;
                }
            }
            return Map.of();
        }

        private String name() {
            position++;
            var start = position;
            while (position < bytes.length && !isDelimiter(bytes[position] & 0xff)) {
                position++;
            }
            return new String(bytes, start, position - start, StandardCharsets.ISO_8859_1);
        }

        private Object wordOrNumber() {
            var start = position;
            while (position < bytes.length && !isDelimiter(bytes[position] & 0xff)) {
                position++;
            }
            var token = new String(bytes, start, position - start, StandardCharsets.ISO_8859_1);
            if (token.isEmpty()) {
                position++;
                return next();
            }
            if (isNumber(token)) {
                try {
                    return Float.parseFloat(token);
                } catch (NumberFormatException ignored) {
                    return token;
                }
            }
            return new Operator(token);
        }

        private static boolean isSpace(int value) {
            return value == 0 || value == 9 || value == 10 || value == 12 || value == 13 || value == 32;
        }

        private static boolean isDelimiter(int value) {
            return isSpace(value) || "()<>[]{}/%".indexOf(value) >= 0;
        }

        private static boolean isNumber(String token) {
            return PdfNumbers.isNumber(token);
        }
    }
}
