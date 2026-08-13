package org.skaldpdf.sign.internal;

import org.skaldpdf.sign.SigningKey;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** Locates and patches the reserved {@code /ByteRange} + {@code /Contents} placeholder. */
public final class SignaturePlaceholder {
    private static final byte[] BYTE_RANGE = "/ByteRange [".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] CONTENTS = "/Contents <".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] PLACEHOLDER_RANGE = "/ByteRange [0 0000000000 0000000000 0000000000]"
        .getBytes(StandardCharsets.US_ASCII);

    private final int firstLengthOffset;
    private final int contentsOpen;
    private final int contentsClose;
    private final int[] byteRange;
    private final String reason;
    private final String location;
    private final String contact;
    private final String pdfDate;
    private final String subFilter;
    private final String fieldName;

    private SignaturePlaceholder(int firstLengthOffset, int contentsOpen, int contentsClose,
                                 int[] byteRange, String reason, String location, String contact,
                                 String pdfDate, String subFilter, String fieldName) {
        this.firstLengthOffset = firstLengthOffset;
        this.contentsOpen = contentsOpen;
        this.contentsClose = contentsClose;
        this.byteRange = byteRange;
        this.reason = reason;
        this.location = location;
        this.contact = contact;
        this.pdfDate = pdfDate;
        this.subFilter = subFilter;
        this.fieldName = fieldName;
    }

    public static SignaturePlaceholder findUnsigned(byte[] pdf) {
        var index = indexOf(pdf, PLACEHOLDER_RANGE, 0);
        if (index < 0) {
            throw new IllegalArgumentException("PDF does not contain an unsigned signature placeholder");
        }
        return parseAt(pdf, index);
    }

    public static List<SignaturePlaceholder> findAll(byte[] pdf) {
        var result = new ArrayList<SignaturePlaceholder>();
        var from = 0;
        while (true) {
            var index = indexOf(pdf, BYTE_RANGE, from);
            if (index < 0) {
                return List.copyOf(result);
            }
            result.add(parseAt(pdf, index));
            from = index + BYTE_RANGE.length;
        }
    }

    public byte[] digest(byte[] pdf) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            digest.update(pdf, byteRange[0], byteRange[1]);
            digest.update(pdf, byteRange[2], byteRange[3]);
            return digest.digest();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    public byte[] contents(byte[] pdf) {
        return Hex.decode(new String(pdf, contentsOpen + 1, contentsClose - contentsOpen - 1,
            StandardCharsets.US_ASCII));
    }

    public int reservedBytes() {
        return (contentsClose - contentsOpen - 1) / 2;
    }

    /**
     * Writes the concrete ByteRange <em>first</em> (those digits are inside the
     * signed region), hashes the ranges, then fills {@code /Contents}.
     */
    public byte[] seal(byte[] pdf, SigningKey key, Instant signingTime) {
        var signed = Arrays.copyOf(pdf, pdf.length);
        writeNumber(signed, firstLengthOffset, contentsOpen);
        writeNumber(signed, firstLengthOffset + 11, contentsClose + 1);
        writeNumber(signed, firstLengthOffset + 22, signed.length - (contentsClose + 1));
        var digest = digest(signed);
        var cms = CmsSignedData.detached(digest, key, signingTime);
        if (cms.length > reservedBytes()) {
            throw new IllegalStateException("CMS (" + cms.length
                + " bytes) exceeds the reserved /Contents (" + reservedBytes() + " bytes)");
        }
        var padded = Arrays.copyOf(cms, reservedBytes());
        var hex = Hex.encode(padded).getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(hex, 0, signed, contentsOpen + 1, hex.length);
        return signed;
    }

    public String reason() {
        return reason;
    }

    public String location() {
        return location;
    }

    public String contact() {
        return contact;
    }

    public String pdfDate() {
        return pdfDate;
    }

    public String subFilter() {
        return subFilter;
    }

    public String fieldName() {
        return fieldName;
    }

    public int[] byteRange() {
        return byteRange.clone();
    }

    private static SignaturePlaceholder parseAt(byte[] pdf, int byteRangeStart) {
        var numbersStart = byteRangeStart + BYTE_RANGE.length;
        var numbers = parseNumbers(pdf, numbersStart, 4);
        var firstLengthOffset = indexOf(pdf, "0000000000".getBytes(StandardCharsets.US_ASCII), numbersStart);
        var contents = indexOf(pdf, CONTENTS, byteRangeStart);
        if (contents < 0) {
            throw new IllegalArgumentException("Signature dictionary is missing /Contents");
        }
        var open = contents + CONTENTS.length - 1;
        var close = indexOf(pdf, new byte[] {'>'}, open + 1);
        if (close < 0) {
            throw new IllegalArgumentException("Signature /Contents hex is not terminated");
        }
        if (numbers[1] == 0 && numbers[2] == 0 && numbers[3] == 0) {
            numbers = new int[] {0, open, close + 1, pdf.length - (close + 1)};
        }
        var before = dictionarySlice(pdf, Math.max(0, byteRangeStart - 96), open);
        var after = dictionarySlice(pdf, close, Math.min(pdf.length, close + 512));
        var dictionary = before + after;
        return new SignaturePlaceholder(
            firstLengthOffset >= 0 ? firstLengthOffset : -1,
            open,
            close,
            numbers,
            dictionaryString(dictionary, "/Reason"),
            dictionaryString(dictionary, "/Location"),
            dictionaryString(dictionary, "/ContactInfo"),
            dictionaryString(dictionary, "/M"),
            dictionaryName(dictionary, "/SubFilter"),
            fieldNameFromWidget(pdf)
        );
    }

    private static String dictionarySlice(byte[] pdf, int from, int to) {
        return new String(pdf, from, Math.max(0, to - from), StandardCharsets.ISO_8859_1);
    }

    private static String fieldNameFromWidget(byte[] pdf) {
        var ascii = new String(pdf, StandardCharsets.ISO_8859_1);
        var from = 0;
        while (true) {
            var sig = ascii.indexOf("/FT /Sig", from);
            if (sig < 0) {
                return "Signature";
            }
            var begin = Math.max(0, sig - 96);
            var end = Math.min(ascii.length(), sig + 160);
            var window = ascii.substring(begin, end);
            var marker = "/T (";
            var index = window.indexOf(marker);
            if (index >= 0) {
                var close = window.indexOf(')', index + marker.length());
                if (close > index) {
                    return window.substring(index + marker.length(), close);
                }
            }
            from = sig + 8;
        }
    }

    private static String dictionaryString(String dictionary, String key) {
        var index = dictionary.indexOf(key + " (");
        if (index < 0) {
            return null;
        }
        var begin = index + key.length() + 2;
        var end = dictionary.indexOf(')', begin);
        return end < 0 ? null : dictionary.substring(begin, end);
    }

    private static String dictionaryName(String dictionary, String key) {
        var index = dictionary.indexOf(key + " /");
        if (index < 0) {
            return null;
        }
        var begin = index + key.length() + 2;
        var end = begin;
        while (end < dictionary.length()) {
            var character = dictionary.charAt(end);
            if (character == ' ' || character == '>' || character == '/' || character == '\n') {
                break;
            }
            end++;
        }
        return dictionary.substring(begin, end);
    }

    private static int[] parseNumbers(byte[] pdf, int from, int count) {
        var result = new int[count];
        var index = from;
        for (int item = 0; item < count; item++) {
            while (index < pdf.length && (pdf[index] == ' ' || pdf[index] == '\n')) {
                index++;
            }
            var start = index;
            while (index < pdf.length && pdf[index] >= '0' && pdf[index] <= '9') {
                index++;
            }
            if (start == index) {
                throw new IllegalArgumentException("Signature /ByteRange is malformed");
            }
            result[item] = Integer.parseInt(new String(pdf, start, index - start, StandardCharsets.US_ASCII));
        }
        return result;
    }

    private static void writeNumber(byte[] pdf, int offset, int value) {
        if (offset < 0) {
            throw new IllegalStateException("Unsigned placeholder /ByteRange digits were not found");
        }
        var digits = String.format(Locale.ROOT, "%010d", value).getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(digits, 0, pdf, offset, 10);
    }

    private static int indexOf(byte[] haystack, byte[] needle, int from) {
        outer:
        for (int index = from; index <= haystack.length - needle.length; index++) {
            for (int item = 0; item < needle.length; item++) {
                if (haystack[index + item] != needle[item]) {
                    continue outer;
                }
            }
            return index;
        }
        return -1;
    }
}
