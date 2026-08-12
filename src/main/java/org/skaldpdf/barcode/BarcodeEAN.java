package org.skaldpdf.barcode;

import org.skaldpdf.pdf.PdfDocument;

import java.util.Objects;

public final class BarcodeEAN {
    public static final int EAN13 = 1;

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

    private int codeType = EAN13;
    private String code;
    private float barHeight = 24f;
    private boolean guardBars = true;
    private float size = 8f;
    private float x = 0.8f;

    public BarcodeEAN(PdfDocument document) {
        Objects.requireNonNull(document, "document");
    }

    public void setCodeType(int codeType) {
        if (codeType != EAN13) {
            throw new IllegalArgumentException("Skald supports EAN-13 barcodes");
        }
        this.codeType = codeType;
    }

    public int getCodeType() {
        return codeType;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    public void setBarHeight(float barHeight) {
        this.barHeight = barHeight;
    }

    public float getBarHeight() {
        return barHeight;
    }

    public void setGuardBars(boolean guardBars) {
        this.guardBars = guardBars;
    }

    public boolean isGuardBars() {
        return guardBars;
    }

    public void setSize(float size) {
        this.size = size;
    }

    public float getSize() {
        return size;
    }

    public void setX(float x) {
        this.x = x;
    }

    public float getX() {
        return x;
    }

    public BarcodeForm createFormXObject(PdfDocument document) {
        Objects.requireNonNull(document, "document");
        if (codeType != EAN13) {
            throw new IllegalStateException("Invalid barcode type");
        }
        var normalized = validateCode(code);
        return new BarcodeForm(normalized, encode(normalized), x, barHeight, size, guardBars);
    }

    public static int calculateEANParity(String value) {
        Objects.requireNonNull(value, "value");
        var total = 0;
        for (int index = value.length() - 1, position = 0; index >= 0; index--, position++) {
            var digit = Character.digit(value.charAt(index), 10);
            if (digit < 0) {
                throw new IllegalArgumentException("EAN values must contain only digits");
            }
            total += digit * (position % 2 == 0 ? 3 : 1);
        }
        return (10 - total % 10) % 10;
    }

    public static byte[] getBarsEAN13(String value) {
        return encode(validateCode(value));
    }

    private static String validateCode(String value) {
        Objects.requireNonNull(value, "Barcode code has not been set");
        if (!value.matches("\\d{12,13}")) {
            throw new IllegalArgumentException("EAN-13 needs 12 payload digits or 13 digits including checksum");
        }
        var complete = value.length() == 12 ? value + calculateEANParity(value) : value;
        var expected = calculateEANParity(complete.substring(0, 12));
        if (Character.digit(complete.charAt(12), 10) != expected) {
            throw new IllegalArgumentException("Invalid EAN-13 checksum");
        }
        return complete;
    }

    private static byte[] encode(String value) {
        var bits = new StringBuilder(95);
        bits.append("101");
        var parity = PARITY[Character.digit(value.charAt(0), 10)];
        for (int index = 1; index <= 6; index++) {
            var digit = Character.digit(value.charAt(index), 10);
            bits.append(parity.charAt(index - 1) == 'L' ? LEFT_ODD[digit] : LEFT_EVEN[digit]);
        }
        bits.append("01010");
        for (int index = 7; index <= 12; index++) {
            bits.append(RIGHT[Character.digit(value.charAt(index), 10)]);
        }
        bits.append("101");
        var modules = new byte[bits.length()];
        for (int index = 0; index < bits.length(); index++) {
            modules[index] = (byte) (bits.charAt(index) - '0');
        }
        return modules;
    }
}
