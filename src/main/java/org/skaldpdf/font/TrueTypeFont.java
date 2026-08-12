package org.skaldpdf.font;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

final class TrueTypeFont {
    private final byte[] source;
    private final Map<String, Table> tables;
    private final int unitsPerEm;
    private final int glyphCount;
    private final int numberOfHorizontalMetrics;
    private final int[] advances;
    private final Cmap cmap;
    private final PdfFont.Metrics metrics;

    TrueTypeFont(byte[] source) {
        this.source = Objects.requireNonNull(source, "source").clone();
        require(source.length >= 12, "Truncated OpenType font");
        var buffer = buffer(source);
        var numberOfTables = unsignedShort(buffer, 4);
        require(numberOfTables > 0 && 12L + numberOfTables * 16L <= source.length,
            "Invalid OpenType table directory");
        var directory = new LinkedHashMap<String, Table>();
        for (int index = 0; index < numberOfTables; index++) {
            var offset = 12 + index * 16;
            var tag = new String(source, offset, 4, java.nio.charset.StandardCharsets.US_ASCII);
            var tableOffset = unsignedInt(buffer, offset + 8);
            var length = unsignedInt(buffer, offset + 12);
            require(tableOffset <= Integer.MAX_VALUE && length <= Integer.MAX_VALUE
                    && tableOffset + length <= source.length,
                "OpenType table is outside the font");
            directory.put(tag, new Table((int) tableOffset, (int) length));
        }
        tables = Map.copyOf(directory);
        var head = table("head");
        var maxp = table("maxp");
        var hhea = table("hhea");
        table("glyf");
        table("loca");
        unitsPerEm = unsignedShort(buffer, head.offset + 18);
        glyphCount = unsignedShort(buffer, maxp.offset + 4);
        numberOfHorizontalMetrics = unsignedShort(buffer, hhea.offset + 34);
        require(unitsPerEm > 0 && glyphCount > 0 && numberOfHorizontalMetrics > 0,
            "Invalid OpenType metrics");
        advances = horizontalAdvances();
        cmap = readCmap();
        var os2 = tables.get("OS/2");
        var post = tables.get("post");
        var capHeight = os2 != null && os2.length >= 90
            ? signedShort(buffer, os2.offset + 88) : signedShort(buffer, hhea.offset + 4);
        var italicAngle = post != null && post.length >= 8 ? fixed(buffer, post.offset + 4) : 0f;
        var fixedPitch = post != null && post.length >= 16 && unsignedInt(buffer, post.offset + 12) != 0;
        metrics = new PdfFont.Metrics(unitsPerEm,
            signedShort(buffer, head.offset + 36), signedShort(buffer, head.offset + 38),
            signedShort(buffer, head.offset + 40), signedShort(buffer, head.offset + 42),
            signedShort(buffer, hhea.offset + 4), signedShort(buffer, hhea.offset + 6),
            capHeight, italicAngle, fixedPitch);
    }

    int glyph(int codePoint) {
        return cmap.glyph(codePoint);
    }

    int pdfWidth(int glyph) {
        require(glyph >= 0 && glyph < advances.length, "Glyph index is outside the font");
        return Math.round(advances[glyph] * 1_000f / unitsPerEm);
    }

    PdfFont.Metrics metrics() {
        return metrics;
    }

    byte[] subset(Set<Integer> requestedGlyphs) {
        return new TrueTypeSubsetter(source, tables, glyphCount).subset(requestedGlyphs);
    }

    private int[] horizontalAdvances() {
        var hmtx = table("hmtx");
        require(hmtx.length >= numberOfHorizontalMetrics * 4, "Truncated hmtx table");
        var result = new int[glyphCount];
        var buffer = buffer(source);
        var last = 0;
        for (int glyph = 0; glyph < glyphCount; glyph++) {
            if (glyph < numberOfHorizontalMetrics) {
                last = unsignedShort(buffer, hmtx.offset + glyph * 4);
            }
            result[glyph] = last;
        }
        return result;
    }

    private Cmap readCmap() {
        var cmapTable = table("cmap");
        var buffer = buffer(source);
        require(cmapTable.length >= 4, "Truncated cmap table");
        var count = unsignedShort(buffer, cmapTable.offset + 2);
        Format4 format4 = null;
        Format12 format12 = null;
        for (int index = 0; index < count; index++) {
            var record = cmapTable.offset + 4 + index * 8;
            require(record + 8 <= cmapTable.offset + cmapTable.length, "Truncated cmap records");
            var platform = unsignedShort(buffer, record);
            var encoding = unsignedShort(buffer, record + 2);
            var subtableOffset = unsignedInt(buffer, record + 4);
            require(subtableOffset <= Integer.MAX_VALUE, "Invalid cmap offset");
            var offset = cmapTable.offset + (int) subtableOffset;
            require(offset + 2 <= cmapTable.offset + cmapTable.length, "cmap subtable is outside the table");
            var format = unsignedShort(buffer, offset);
            if (format == 12 && (platform == 0 || platform == 3) && format12 == null) {
                format12 = new Format12(source, offset, cmapTable.offset + cmapTable.length);
            } else if (format == 4 && (platform == 0 || platform == 3)
                && (encoding == 1 || encoding == 10 || platform == 0) && format4 == null) {
                format4 = new Format4(source, offset, cmapTable.offset + cmapTable.length);
            }
        }
        require(format4 != null || format12 != null, "Font has no supported Unicode cmap");
        var bmp = format4;
        var full = format12;
        return codePoint -> {
            var glyph = full == null ? 0 : full.glyph(codePoint);
            return glyph != 0 || bmp == null ? glyph : bmp.glyph(codePoint);
        };
    }

    private Table table(String name) {
        var result = tables.get(name);
        if (result == null) {
            throw new IllegalArgumentException("Required OpenType table is missing: " + name);
        }
        return result;
    }

    static ByteBuffer buffer(byte[] bytes) {
        return ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN);
    }

    static int unsignedShort(ByteBuffer buffer, int offset) {
        return Short.toUnsignedInt(buffer.getShort(offset));
    }

    static int signedShort(ByteBuffer buffer, int offset) {
        return buffer.getShort(offset);
    }

    static long unsignedInt(ByteBuffer buffer, int offset) {
        return Integer.toUnsignedLong(buffer.getInt(offset));
    }

    private static float fixed(ByteBuffer buffer, int offset) {
        return buffer.getInt(offset) / 65536f;
    }

    static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }

    record Table(int offset, int length) {
    }

    private interface Cmap {
        int glyph(int codePoint);
    }

    private static final class Format12 implements Cmap {
        private final int[] starts;
        private final int[] ends;
        private final int[] startGlyphs;

        Format12(byte[] bytes, int offset, int tableEnd) {
            var buffer = buffer(bytes);
            var length = unsignedInt(buffer, offset + 4);
            var groups = unsignedInt(buffer, offset + 12);
            require(length <= Integer.MAX_VALUE && groups <= Integer.MAX_VALUE
                && offset + length <= tableEnd && 16L + groups * 12L <= length, "Invalid cmap format 12");
            starts = new int[(int) groups];
            ends = new int[(int) groups];
            startGlyphs = new int[(int) groups];
            for (int index = 0; index < starts.length; index++) {
                var group = offset + 16 + index * 12;
                starts[index] = buffer.getInt(group);
                ends[index] = buffer.getInt(group + 4);
                startGlyphs[index] = buffer.getInt(group + 8);
            }
        }

        @Override
        public int glyph(int codePoint) {
            var low = 0;
            var high = starts.length - 1;
            while (low <= high) {
                var middle = (low + high) >>> 1;
                if (codePoint < starts[middle]) {
                    high = middle - 1;
                } else if (codePoint > ends[middle]) {
                    low = middle + 1;
                } else {
                    return startGlyphs[middle] + codePoint - starts[middle];
                }
            }
            return 0;
        }
    }

    private static final class Format4 implements Cmap {
        private final byte[] bytes;
        private final int offset;
        private final int segmentCount;
        private final int endCodes;
        private final int startCodes;
        private final int deltas;
        private final int rangeOffsets;

        Format4(byte[] bytes, int offset, int tableEnd) {
            this.bytes = bytes;
            this.offset = offset;
            var buffer = buffer(bytes);
            var length = unsignedShort(buffer, offset + 2);
            require(offset + length <= tableEnd && length >= 16, "Invalid cmap format 4");
            segmentCount = unsignedShort(buffer, offset + 6) / 2;
            endCodes = offset + 14;
            startCodes = endCodes + segmentCount * 2 + 2;
            deltas = startCodes + segmentCount * 2;
            rangeOffsets = deltas + segmentCount * 2;
            require(rangeOffsets + segmentCount * 2 <= offset + length, "Truncated cmap format 4");
        }

        @Override
        public int glyph(int codePoint) {
            if (codePoint < 0 || codePoint > 0xffff) {
                return 0;
            }
            var buffer = buffer(bytes);
            for (int segment = 0; segment < segmentCount; segment++) {
                var end = unsignedShort(buffer, endCodes + segment * 2);
                if (codePoint > end) {
                    continue;
                }
                var start = unsignedShort(buffer, startCodes + segment * 2);
                if (codePoint < start) {
                    return 0;
                }
                var delta = signedShort(buffer, deltas + segment * 2);
                var range = unsignedShort(buffer, rangeOffsets + segment * 2);
                if (range == 0) {
                    return (codePoint + delta) & 0xffff;
                }
                var glyphOffset = rangeOffsets + segment * 2 + range + (codePoint - start) * 2;
                if (glyphOffset + 2 > bytes.length) {
                    return 0;
                }
                var glyph = unsignedShort(buffer, glyphOffset);
                return glyph == 0 ? 0 : (glyph + delta) & 0xffff;
            }
            return 0;
        }
    }
}
