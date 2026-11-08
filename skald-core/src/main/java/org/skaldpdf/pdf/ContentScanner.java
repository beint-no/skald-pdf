package org.skaldpdf.pdf;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Tokenizes a page or Form content stream into operands (numbers, strings, names,
 * arrays) and keywords classified by the caller. Dictionaries are skipped.
 */
final class ContentScanner<K> {
    /** Classifies a keyword token without allocating; never returns null. */
    interface Keywords<K> {
        K of(byte[] bytes, int start, int length);
    }

    private final Keywords<K> keywords;
    private final K otherKeyword;
    private final byte[] bytes;
    private int position;

    ContentScanner(byte[] bytes, Keywords<K> keywords, K otherKeyword) {
        this.bytes = bytes;
        this.keywords = keywords;
        this.otherKeyword = otherKeyword;
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
        var length = position - start;
        if (length == 0) {
            position++;
            return next();
        }
        var first = bytes[start];
        if (first != '+' && first != '-' && first != '.' && (first < '0' || first > '9')) {
            // Cannot be a number, so it is a keyword: classify it without allocating a string.
            return keywords.of(bytes, start, length);
        }
        var token = new String(bytes, start, length, StandardCharsets.ISO_8859_1);
        if (isNumber(token)) {
            try {
                return Float.parseFloat(token);
            } catch (NumberFormatException ignored) {
                return token;
            }
        }
        return otherKeyword;
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
