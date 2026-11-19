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
        "head", "hhea", "maxp", "OS/2", "name", "cmap", "hmtx", "loca", "glyf"
    );
    private static final int ARG_1_AND_2_ARE_WORDS = 0x0001;
    private static final int WE_HAVE_A_SCALE = 0x0008;
    private static final int MORE_COMPONENTS = 0x0020;
    private static final int WE_HAVE_AN_X_AND_Y_SCALE = 0x0040;
    private static final int WE_HAVE_A_TWO_BY_TWO = 0x0080;
    private static final int WE_HAVE_INSTRUCTIONS = 0x0100;
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
                var raw = Arrays.copyOfRange(source, glyf.offset() + start, glyf.offset() + end);
                var outline = raw.length == 0 ? raw : stripInstructions(raw);
                glyfOutput.writeBytes(outline);
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
        var maxp = replacementTables.get("maxp");
        if (maxp != null && maxp.length >= 32) {
            var updatedMaxp = maxp.clone();
            var maxpBuffer = ByteBuffer.wrap(updatedMaxp).order(ByteOrder.BIG_ENDIAN);
            if (maxpBuffer.getInt(0) == 0x0001_0000) {
                maxpBuffer.putShort(14, (short) 1);
                for (var field : new int[] {16, 18, 20, 22, 24, 26}) {
                    maxpBuffer.putShort(field, (short) 0);
                }
                replacementTables.put("maxp", updatedMaxp);
            }
        }
        return assemble(replacementTables);
    }

    private static byte[] stripInstructions(byte[] glyph) {
        if (glyph.length < 10) {
            return glyph;
        }
        var contours = s16(glyph, 0);
        if (contours >= 0) {
            var lengthAt = 10 + 2 * contours;
            if (lengthAt + 2 > glyph.length) {
                return glyph;
            }
            var instructions = u16(glyph, lengthAt);
            if (instructions == 0 || lengthAt + 2 + instructions > glyph.length) {
                return glyph;
            }
            var points = contours == 0 ? 0 : u16(glyph, lengthAt - 2) + 1;
            var dataStart = lengthAt + 2 + instructions;
            var dataEnd = pointDataEnd(glyph, dataStart, points);
            if (dataEnd < 0) {
                return glyph;
            }
            var output = new ByteArrayOutputStream(glyph.length - instructions);
            output.write(glyph, 0, lengthAt);
            output.write(0);
            output.write(0);
            output.write(glyph, dataStart, dataEnd - dataStart);
            return output.toByteArray();
        }
        var copy = glyph.clone();
        var position = 10;
        int flags;
        var withInstructions = false;
        do {
            if (position + 4 > copy.length) {
                return glyph;
            }
            flags = u16(copy, position);
            withInstructions |= (flags & WE_HAVE_INSTRUCTIONS) != 0;
            put16(copy, position, flags & ~WE_HAVE_INSTRUCTIONS);
            position += 4 + ((flags & ARG_1_AND_2_ARE_WORDS) != 0 ? 4 : 2);
            if ((flags & WE_HAVE_A_SCALE) != 0) {
                position += 2;
            } else if ((flags & WE_HAVE_AN_X_AND_Y_SCALE) != 0) {
                position += 4;
            } else if ((flags & WE_HAVE_A_TWO_BY_TWO) != 0) {
                position += 8;
            }
            if (position > copy.length) {
                return glyph;
            }
        } while ((flags & MORE_COMPONENTS) != 0);
        if (!withInstructions) {
            return glyph;
        }
        if (position + 2 > copy.length) {
            return glyph;
        }
        var instructions = u16(copy, position);
        if (position + 2 + instructions > copy.length) {
            return glyph;
        }
        return Arrays.copyOf(copy, position);
    }

    private static int pointDataEnd(byte[] glyph, int start, int points) {
        var flags = new int[points];
        var position = start;
        for (int point = 0; point < points;) {
            if (position >= glyph.length) {
                return -1;
            }
            var flag = glyph[position++] & 0xff;
            var repeat = 0;
            if ((flag & 0x08) != 0) {
                if (position >= glyph.length) {
                    return -1;
                }
                repeat = glyph[position++] & 0xff;
            }
            for (int copy = 0; copy <= repeat; copy++) {
                if (point >= points) {
                    return -1;
                }
                flags[point++] = flag;
            }
        }
        for (var flag : flags) {
            position += (flag & 0x02) != 0 ? 1 : (flag & 0x10) != 0 ? 0 : 2;
        }
        for (var flag : flags) {
            position += (flag & 0x04) != 0 ? 1 : (flag & 0x20) != 0 ? 0 : 2;
        }
        return position <= glyph.length ? position : -1;
    }

    private static int u16(byte[] data, int offset) {
        return (data[offset] & 0xff) << 8 | data[offset + 1] & 0xff;
    }

    private static int s16(byte[] data, int offset) {
        return (short) u16(data, offset);
    }

    private static void put16(byte[] data, int offset, int value) {
        data[offset] = (byte) (value >>> 8);
        data[offset + 1] = (byte) value;
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
