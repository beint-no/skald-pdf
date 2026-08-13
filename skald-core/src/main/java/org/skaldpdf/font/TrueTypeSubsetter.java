package org.skaldpdf.font;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

final class TrueTypeSubsetter {
    private static final long CHECKSUM_MAGIC = 0xB1B0AFBAL;
    private static final Set<String> KEPT_TABLES = Set.of(
        "head", "hhea", "maxp", "OS/2", "name", "cmap", "hmtx", "loca", "glyf",
        "cvt ", "fpgm", "prep", "gasp"
    );
    private final byte[] source;
    private final Map<String, TrueTypeFont.Table> tables;
    private final int glyphCount;
    private final int[] loca;

    TrueTypeSubsetter(byte[] source, Map<String, TrueTypeFont.Table> tables, int glyphCount) {
        this.source = source;
        this.tables = tables;
        this.glyphCount = glyphCount;
        loca = readLoca();
    }

    byte[] subset(Set<Integer> requestedGlyphs) {
        var used = compositeClosure(requestedGlyphs);
        var glyfOutput = new ByteArrayOutputStream();
        var subsetLoca = new int[glyphCount + 1];
        var glyf = requiredTable("glyf");
        for (int glyph = 0; glyph < glyphCount; glyph++) {
            subsetLoca[glyph] = glyfOutput.size();
            if (used.contains(glyph)) {
                var start = loca[glyph];
                var end = loca[glyph + 1];
                TrueTypeFont.require(start >= 0 && end >= start && end <= glyf.length(), "Invalid glyph bounds");
                glyfOutput.write(source, glyf.offset() + start, end - start);
                while ((glyfOutput.size() & 3) != 0) {
                    glyfOutput.write(0);
                }
            }
        }
        subsetLoca[glyphCount] = glyfOutput.size();

        var replacementTables = new LinkedHashMap<String, byte[]>();
        for (var name : KEPT_TABLES) {
            var table = tables.get(name);
            if (table != null) {
                replacementTables.put(name, Arrays.copyOfRange(source, table.offset(), table.offset() + table.length()));
            }
        }
        replacementTables.put("glyf", glyfOutput.toByteArray());
        var longLoca = ByteBuffer.allocate(subsetLoca.length * Integer.BYTES).order(ByteOrder.BIG_ENDIAN);
        Arrays.stream(subsetLoca).forEach(longLoca::putInt);
        replacementTables.put("loca", longLoca.array());
        replacementTables.put("post", format3Post());
        var head = replacementTables.get("head").clone();
        ByteBuffer.wrap(head).order(ByteOrder.BIG_ENDIAN).putShort(50, (short) 1).putInt(8, 0);
        replacementTables.put("head", head);
        return assemble(replacementTables);
    }

    private byte[] format3Post() {
        var post = new byte[32];
        var original = tables.get("post");
        if (original != null && original.length() >= 32) {
            System.arraycopy(source, original.offset(), post, 0, 32);
        }
        ByteBuffer.wrap(post).order(ByteOrder.BIG_ENDIAN).putInt(0, 0x0003_0000);
        return post;
    }

    private Set<Integer> compositeClosure(Set<Integer> requested) {
        var result = new LinkedHashSet<Integer>();
        result.add(0);
        requested.forEach(glyph -> {
            TrueTypeFont.require(glyph >= 0 && glyph < glyphCount, "Glyph index is outside the font");
            result.add(glyph);
        });
        var queue = new ArrayList<>(result);
        for (int cursor = 0; cursor < queue.size(); cursor++) {
            var glyph = queue.get(cursor);
            var start = loca[glyph];
            var end = loca[glyph + 1];
            if (end - start < 10) {
                continue;
            }
            var glyf = requiredTable("glyf");
            var offset = glyf.offset() + start;
            var buffer = TrueTypeFont.buffer(source);
            if (buffer.getShort(offset) >= 0) {
                continue;
            }
            var component = offset + 10;
            var more = true;
            while (more) {
                TrueTypeFont.require(component + 4 <= glyf.offset() + end, "Truncated composite glyph");
                var flags = TrueTypeFont.unsignedShort(buffer, component);
                var child = TrueTypeFont.unsignedShort(buffer, component + 2);
                if (result.add(child)) {
                    queue.add(child);
                }
                component += 4;
                component += (flags & 0x0001) != 0 ? 4 : 2;
                if ((flags & 0x0008) != 0) {
                    component += 2;
                } else if ((flags & 0x0040) != 0) {
                    component += 4;
                } else if ((flags & 0x0080) != 0) {
                    component += 8;
                }
                more = (flags & 0x0020) != 0;
            }
        }
        return Set.copyOf(result);
    }

    private int[] readLoca() {
        var head = requiredTable("head");
        var locaTable = requiredTable("loca");
        var buffer = TrueTypeFont.buffer(source);
        var format = TrueTypeFont.signedShort(buffer, head.offset() + 50);
        var result = new int[glyphCount + 1];
        var entrySize = format == 0 ? Short.BYTES : Integer.BYTES;
        TrueTypeFont.require(locaTable.length() >= result.length * entrySize, "Truncated loca table");
        for (int index = 0; index < result.length; index++) {
            result[index] = format == 0
                ? TrueTypeFont.unsignedShort(buffer, locaTable.offset() + index * entrySize) * 2
                : buffer.getInt(locaTable.offset() + index * entrySize);
        }
        return result;
    }

    private byte[] assemble(Map<String, byte[]> tableData) {
        var entries = tableData.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList();
        var count = entries.size();
        var maximumPower = Integer.highestOneBit(count);
        var searchRange = maximumPower * 16;
        var entrySelector = Integer.numberOfTrailingZeros(maximumPower);
        var rangeShift = count * 16 - searchRange;
        var headerSize = 12 + count * 16;
        var offsets = new LinkedHashMap<String, Integer>();
        var totalSize = headerSize;
        for (var entry : entries) {
            totalSize = aligned(totalSize);
            offsets.put(entry.getKey(), totalSize);
            totalSize += aligned(entry.getValue().length);
        }
        var output = ByteBuffer.allocate(totalSize).order(ByteOrder.BIG_ENDIAN);
        output.putInt(0x00010000).putShort((short) count).putShort((short) searchRange)
            .putShort((short) entrySelector).putShort((short) rangeShift);
        for (var entry : entries) {
            output.put(entry.getKey().getBytes(StandardCharsets.US_ASCII));
            output.putInt((int) checksum(entry.getValue()));
            output.putInt(offsets.get(entry.getKey()));
            output.putInt(entry.getValue().length);
        }
        for (var entry : entries) {
            output.position(offsets.get(entry.getKey()));
            output.put(entry.getValue());
        }
        var bytes = output.array();
        var adjustment = (CHECKSUM_MAGIC - checksum(bytes)) & 0xffff_ffffL;
        ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
            .putInt(offsets.get("head") + 8, (int) adjustment);
        return bytes;
    }

    private static long checksum(byte[] bytes) {
        var padded = Arrays.copyOf(bytes, aligned(bytes.length));
        var buffer = ByteBuffer.wrap(padded).order(ByteOrder.BIG_ENDIAN);
        long result = 0;
        while (buffer.remaining() >= 4) {
            result = (result + Integer.toUnsignedLong(buffer.getInt())) & 0xffff_ffffL;
        }
        return result;
    }

    private TrueTypeFont.Table requiredTable(String name) {
        var result = tables.get(name);
        if (result == null) {
            throw new IllegalArgumentException("Required OpenType table is missing: " + name);
        }
        return result;
    }

    private static int aligned(int value) {
        return (value + 3) & ~3;
    }
}
