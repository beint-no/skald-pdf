package org.skaldpdf.sign.internal;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Small DER cursor for the CMS structures Skald verifies. */
public final class DerReader {
    private final byte[] data;
    private int offset;
    private final int end;

    public DerReader(byte[] data) {
        this(data, 0, data.length);
    }

    public DerReader(byte[] data, int offset, int end) {
        this.data = data;
        this.offset = offset;
        this.end = end;
    }

    public static DerReader of(byte[] encoded) {
        return new DerReader(encoded);
    }

    public boolean hasMore() {
        return offset < end;
    }

    public int peekTag() {
        require(1);
        return data[offset] & 0xff;
    }

    public byte[] readEncoded() {
        var start = offset;
        var header = tagLengthHeaderSize();
        var length = lengthAt(start);
        offset = start + header + length;
        if (offset > end) {
            throw new IllegalArgumentException("Truncated DER");
        }
        return Arrays.copyOfRange(data, start, offset);
    }

    public DerReader enter(int expectedTag) {
        if (peekTag() != expectedTag) {
            throw new IllegalArgumentException("Expected DER tag 0x" + Integer.toHexString(expectedTag)
                + " but found 0x" + Integer.toHexString(peekTag()));
        }
        return enter();
    }

    public DerReader enter() {
        var start = offset;
        var header = tagLengthHeaderSize();
        var length = lengthAt(start);
        offset = start + header + length;
        if (offset > end) {
            throw new IllegalArgumentException("Truncated DER");
        }
        return new DerReader(data, start + header, start + header + length);
    }

    public BigInteger integer() {
        return new BigInteger(enter(0x02).remaining());
    }

    public byte[] octetString() {
        return enter(0x04).remaining();
    }

    public byte[] oidEncoded() {
        return readEncodedIfTag(0x06);
    }

    public boolean isOid(byte[] encodedOid) {
        return peekTag() == 0x06 && Arrays.equals(readEncodedIfTag(0x06), encodedOid);
    }

    public List<byte[]> childrenEncoded() {
        var result = new ArrayList<byte[]>();
        while (hasMore()) {
            result.add(readEncoded());
        }
        return result;
    }

    public byte[] remaining() {
        return Arrays.copyOfRange(data, offset, end);
    }

    public byte[] contentAsSet() {
        return Der.constructed(0x31, remaining());
    }

    private byte[] readEncodedIfTag(int tag) {
        if (peekTag() != tag) {
            throw new IllegalArgumentException("Expected DER tag 0x" + Integer.toHexString(tag));
        }
        return readEncoded();
    }

    private int tagLengthHeaderSize() {
        require(2);
        var first = data[offset + 1] & 0xff;
        return first < 0x80 ? 2 : 2 + (first & 0x7f);
    }

    private int lengthAt(int start) {
        requireFrom(start, 2);
        var first = data[start + 1] & 0xff;
        if (first < 0x80) {
            return first;
        }
        var count = first & 0x7f;
        if (count == 0 || count > 3) {
            throw new IllegalArgumentException("Unsupported DER length");
        }
        requireFrom(start, 2 + count);
        var length = 0;
        for (int index = 0; index < count; index++) {
            length = (length << 8) | (data[start + 2 + index] & 0xff);
        }
        return length;
    }

    private void require(int count) {
        requireFrom(offset, count);
    }

    private void requireFrom(int start, int count) {
        if (start + count > end) {
            throw new IllegalArgumentException("Truncated DER");
        }
    }
}
