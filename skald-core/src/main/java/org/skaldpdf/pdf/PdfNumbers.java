package org.skaldpdf.pdf;

/** Compact PDF numeric and hex encoding used by writers and content streams. */
public final class PdfNumbers {
    private static final char[] HEX = "0123456789ABCDEF".toCharArray();

    private PdfNumbers() {
    }

    static boolean isNumber(String token) {
        return isNumeric(token, true);
    }

    static boolean isInteger(String token) {
        return isNumeric(token, false);
    }

    // PDF numbers use ASCII digits, an optional leading sign and at most one
    // decimal point. Unlike Java numbers, exponents and Unicode digits are not allowed.
    private static boolean isNumeric(String token, boolean allowDecimalPoint) {
        if (token.isEmpty()) {
            return false;
        }
        var first = token.charAt(0);
        var start = first == '+' || first == '-' ? 1 : 0;
        var hasDigit = false;
        for (int index = start; index < token.length(); index++) {
            var character = token.charAt(index);
            if (character >= '0' && character <= '9') {
                hasDigit = true;
            } else if (character == '.' && allowDecimalPoint) {
                allowDecimalPoint = false;
            } else {
                return false;
            }
        }
        return hasDigit;
    }

    public static String format(float value) {
        var output = new StringBuilder(12);
        append(output, value);
        return output.toString();
    }

    public static void append(StringBuilder output, float value) {
        if (!Float.isFinite(value)) {
            throw new IllegalArgumentException("PDF number must be finite");
        }
        if (value == 0f) {
            output.append('0');
            return;
        }
        var negative = value < 0f;
        var absolute = negative ? -value : value;
        if (absolute >= 1.0e8f) {
            throw new IllegalArgumentException("PDF number is outside the supported range");
        }
        var scaled = Math.round((double) absolute * 100_000d);
        if (scaled == 0L) {
            output.append('0');
            return;
        }
        if (negative) {
            output.append('-');
        }
        output.append(scaled / 100_000L);
        var fraction = scaled % 100_000L;
        if (fraction != 0L) {
            output.append('.');
            var digits = new char[5];
            for (int index = 4; index >= 0; index--) {
                digits[index] = (char) ('0' + (fraction % 10L));
                fraction /= 10L;
            }
            var last = 4;
            while (last > 0 && digits[last] == '0') {
                last--;
            }
            output.append(digits, 0, last + 1);
        }
    }

    public static void appendHex4(StringBuilder output, int value) {
        if (value < 0 || value > 0xffff) {
            throw new IllegalArgumentException("Glyph index exceeds Identity-H encoding");
        }
        output.append(HEX[(value >>> 12) & 0xf]);
        output.append(HEX[(value >>> 8) & 0xf]);
        output.append(HEX[(value >>> 4) & 0xf]);
        output.append(HEX[value & 0xf]);
    }
}
