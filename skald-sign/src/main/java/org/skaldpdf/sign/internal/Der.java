package org.skaldpdf.sign.internal;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/** Minimal DER encoder for the CMS SignedData subset Skald emits. */
public final class Der {
    private Der() {
    }

    public static byte[] sequence(byte[]... children) {
        return constructed(0x30, concat(children));
    }

    public static byte[] set(byte[]... children) {
        var sorted = new ArrayList<>(List.of(children));
        sorted.sort(Comparator.comparing(Der::hex));
        return constructed(0x31, concat(sorted.toArray(byte[][]::new)));
    }

    public static byte[] implicitSet(int tag, byte[]... children) {
        var sorted = new ArrayList<>(List.of(children));
        sorted.sort(Comparator.comparing(Der::hex));
        return constructed(0xa0 + tag, concat(sorted.toArray(byte[][]::new)));
    }

    public static byte[] integer(int value) {
        return integer(BigInteger.valueOf(value));
    }

    public static byte[] integer(BigInteger value) {
        var bytes = value.toByteArray();
        return primitive(0x02, bytes);
    }

    public static byte[] octetString(byte[] value) {
        return primitive(0x04, value);
    }

    public static byte[] oid(int... arcs) {
        if (arcs.length < 2) {
            throw new IllegalArgumentException("An OBJECT IDENTIFIER needs at least two arcs");
        }
        var body = new ByteArrayOutputStream();
        body.write(40 * arcs[0] + arcs[1]);
        for (int index = 2; index < arcs.length; index++) {
            writeBase128(body, arcs[index]);
        }
        return primitive(0x06, body.toByteArray());
    }

    public static byte[] nullValue() {
        return primitive(0x05, new byte[0]);
    }

    public static byte[] algorithm(byte[] oid) {
        return sequence(oid, nullValue());
    }

    public static byte[] utf8String(String value) {
        return primitive(0x0c, value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    public static byte[] printableString(String value) {
        return primitive(0x13, value.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
    }

    public static byte[] utcTime(String yyMMddHHmmssZ) {
        return primitive(0x17, yyMMddHHmmssZ.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
    }

    public static byte[] booleanValue(boolean value) {
        return primitive(0x01, new byte[] {value ? (byte) 0xff : 0});
    }

    public static byte[] bitString(int unusedBits, byte[] value) {
        if (unusedBits < 0 || unusedBits > 7) {
            throw new IllegalArgumentException("BIT STRING unused bits must be 0-7");
        }
        var body = new byte[value.length + 1];
        body[0] = (byte) unusedBits;
        System.arraycopy(value, 0, body, 1, value.length);
        return primitive(0x03, body);
    }

    public static byte[] explicit(int tag, byte[] encoded) {
        return constructed(0xa0 + tag, encoded);
    }

    public static byte[] raw(byte[] encoded) {
        return encoded.clone();
    }

    public static byte[] concat(byte[]... parts) {
        var length = 0;
        for (var part : parts) {
            length += part.length;
        }
        var result = new byte[length];
        var offset = 0;
        for (var part : parts) {
            System.arraycopy(part, 0, result, offset, part.length);
            offset += part.length;
        }
        return result;
    }

    public static byte[] constructed(int tag, byte[] body) {
        return encode(tag, body);
    }

    public static byte[] primitive(int tag, byte[] body) {
        return encode(tag, body);
    }

    private static byte[] encode(int tag, byte[] body) {
        var length = lengthBytes(body.length);
        var result = new byte[1 + length.length + body.length];
        result[0] = (byte) tag;
        System.arraycopy(length, 0, result, 1, length.length);
        System.arraycopy(body, 0, result, 1 + length.length, body.length);
        return result;
    }

    private static byte[] lengthBytes(int length) {
        if (length < 0x80) {
            return new byte[] {(byte) length};
        }
        if (length < 0x100) {
            return new byte[] {(byte) 0x81, (byte) length};
        }
        if (length < 0x10000) {
            return new byte[] {(byte) 0x82, (byte) (length >>> 8), (byte) length};
        }
        return new byte[] {
            (byte) 0x83, (byte) (length >>> 16), (byte) (length >>> 8), (byte) length
        };
    }

    private static void writeBase128(ByteArrayOutputStream output, int value) {
        if (value < 0) {
            throw new IllegalArgumentException("OID arc must be non-negative");
        }
        var stack = new byte[5];
        var count = 0;
        stack[count++] = (byte) (value & 0x7f);
        value >>>= 7;
        while (value > 0) {
            stack[count++] = (byte) ((value & 0x7f) | 0x80);
            value >>>= 7;
        }
        for (int index = count - 1; index >= 0; index--) {
            output.write(stack[index]);
        }
    }

    private static String hex(byte[] bytes) {
        var result = new StringBuilder(bytes.length * 2);
        for (var value : bytes) {
            result.append(String.format("%02X", value & 0xff));
        }
        return result.toString();
    }

    public static byte[] skipTagLength(byte[] encoded) {
        if (encoded.length < 2) {
            throw new IllegalArgumentException("Truncated DER");
        }
        var length = encoded[1] & 0xff;
        var header = 2;
        if (length >= 0x80) {
            var count = length & 0x7f;
            header = 2 + count;
        }
        return Arrays.copyOfRange(encoded, header, encoded.length);
    }
}
