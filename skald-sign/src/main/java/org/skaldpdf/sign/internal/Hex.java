package org.skaldpdf.sign.internal;

public final class Hex {
    private static final char[] DIGITS = "0123456789abcdef".toCharArray();

    private Hex() {
    }

    public static String encode(byte[] bytes) {
        var result = new StringBuilder(bytes.length * 2);
        for (var value : bytes) {
            result.append(DIGITS[(value >>> 4) & 0x0f]);
            result.append(DIGITS[value & 0x0f]);
        }
        return result.toString();
    }

    public static byte[] decode(CharSequence hex) {
        var digits = new StringBuilder(hex.length());
        for (int index = 0; index < hex.length(); index++) {
            var character = hex.charAt(index);
            if (character == ' ' || character == '\n' || character == '\r' || character == '\t') {
                continue;
            }
            digits.append(character);
        }
        if ((digits.length() & 1) != 0) {
            throw new IllegalArgumentException("Hex string must have an even length");
        }
        var result = new byte[digits.length() / 2];
        for (int index = 0; index < result.length; index++) {
            result[index] = (byte) ((fromHex(digits.charAt(index * 2)) << 4)
                | fromHex(digits.charAt(index * 2 + 1)));
        }
        return result;
    }

    private static int fromHex(char character) {
        if (character >= '0' && character <= '9') {
            return character - '0';
        }
        if (character >= 'a' && character <= 'f') {
            return character - 'a' + 10;
        }
        if (character >= 'A' && character <= 'F') {
            return character - 'A' + 10;
        }
        throw new IllegalArgumentException("Not a hex digit: " + character);
    }
}
