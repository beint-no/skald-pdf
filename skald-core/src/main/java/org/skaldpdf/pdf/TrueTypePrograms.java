package org.skaldpdf.pdf;

import org.jspecify.annotations.Nullable;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Set;
import java.util.TreeMap;

/**
 * Shrinks embedded TrueType programs without changing any glyph a page draws.
 *
 * <p>{@link #withoutHinting} removes hinting and text-layout tables.
 * {@link #withoutUnusedGlyphs} empties the outlines and metrics of glyph IDs no
 * page uses while keeping every glyph ID stable.
 *
 * <p>PDF content positions every glyph itself, so layout tables (GSUB, GPOS, GDEF,
 * kern, ...) are never consulted, and hinting only tunes rasterisation on
 * low-resolution screens. Glyph IDs, outlines, metrics, cmap, post and name stay
 * byte for byte; each glyph only loses its instruction bytes. Anything unexpected
 * (CFF outlines, collections, malformed tables) keeps the original program.
 */
final class TrueTypePrograms {
    private static final Set<String> REMOVED_TABLES = Set.of(
        "fpgm", "prep", "cvt ", "hdmx", "VDMX", "LTSH", "gasp",
        "GPOS", "GSUB", "GDEF", "JSTF", "BASE", "kern", "morx", "feat", "DSIG");
    private static final int ARG_1_AND_2_ARE_WORDS = 0x0001;
    private static final int WE_HAVE_A_SCALE = 0x0008;
    private static final int MORE_COMPONENTS = 0x0020;
    private static final int WE_HAVE_AN_X_AND_Y_SCALE = 0x0040;
    private static final int WE_HAVE_A_TWO_BY_TWO = 0x0080;
    private static final int WE_HAVE_INSTRUCTIONS = 0x0100;

    private TrueTypePrograms() {
    }

    /** The program without hinting and layout tables, or null when it would not change or is not plain TrueType. */
    static byte @Nullable [] withoutHinting(byte[] font) {
        try {
            var result = rewrite(font);
            return result != null && result.length < font.length ? result : null;
        } catch (RuntimeException malformed) {
            return null;
        }
    }

    /**
     * The program with every glyph outside {@code used} (and the glyphs their
     * composites reference) reduced to an empty outline with zero metrics, or null
     * when nothing changes or the program is not plain TrueType. Glyph IDs, glyph 0,
     * {@code cmap}, {@code post} and all other tables stay as they are.
     */
    static byte @Nullable [] withoutUnusedGlyphs(byte[] font, BitSet used) {
        try {
            // Zeroed metrics keep the size of the program; the caller keeps it only when it compresses smaller.
            return prune(font, used);
        } catch (RuntimeException malformed) {
            return null;
        }
    }

    private static byte @Nullable [] prune(byte[] font, BitSet used) {
        var version = u32(font, 0);
        var tables = tables(font);
        if (tables == null) {
            return null;
        }
        var head = tables.get("head");
        var maxp = tables.get("maxp");
        var glyf = tables.get("glyf");
        var hhea = tables.get("hhea");
        var hmtx = tables.get("hmtx");
        require(hhea != null && hhea.length >= 36 && hmtx != null);
        var glyphCount = u16(maxp, 4);
        var longOffsets = s16(head, 50) == 1;
        var offsets = offsets(tables, glyphCount);
        var metrics = u16(hhea, 34);
        require(metrics >= 1 && metrics <= glyphCount && hmtx.length >= 4L * metrics + 2L * (glyphCount - metrics));

        var kept = new BitSet(glyphCount);
        kept.set(0);
        var pending = new java.util.ArrayDeque<Integer>();
        pending.add(0);
        for (int glyph = used.nextSetBit(0); glyph >= 0 && glyph < glyphCount; glyph = used.nextSetBit(glyph + 1)) {
            if (!kept.get(glyph)) {
                kept.set(glyph);
                pending.add(glyph);
            }
        }
        while (!pending.isEmpty()) {
            var glyph = pending.poll();
            var start = (int) offsets[glyph];
            var end = (int) offsets[glyph + 1];
            if (end - start >= 10 && s16(glyf, start) < 0) {
                for (var component : components(glyf, start, end)) {
                    require(component < glyphCount);
                    if (!kept.get(component)) {
                        kept.set(component);
                        pending.add(component);
                    }
                }
            }
        }

        var changed = false;
        var glyphs = new ByteArrayOutputStream(glyf.length);
        var newOffsets = new long[glyphCount + 1];
        for (int index = 0; index < glyphCount; index++) {
            newOffsets[index] = glyphs.size();
            var length = (int) (offsets[index + 1] - offsets[index]);
            if (kept.get(index)) {
                glyphs.write(glyf, (int) offsets[index], length);
                while (glyphs.size() % (longOffsets ? 4 : 2) != 0) {
                    glyphs.write(0);
                }
            } else {
                changed |= length != 0;
            }
        }
        newOffsets[glyphCount] = glyphs.size();
        var newHmtx = hmtx.clone();
        for (int index = 0; index < glyphCount; index++) {
            if (kept.get(index)) {
                continue;
            }
            if (index < metrics) {
                // The last full metric also supplies the advance of every later glyph.
                var position = 4 * index;
                if (index != metrics - 1 && (u16(newHmtx, position) != 0 || u16(newHmtx, position + 2) != 0)) {
                    put16(newHmtx, position, 0);
                    put16(newHmtx, position + 2, 0);
                    changed = true;
                }
            } else {
                var position = 4 * metrics + 2 * (index - metrics);
                if (u16(newHmtx, position) != 0) {
                    put16(newHmtx, position, 0);
                    changed = true;
                }
            }
        }
        if (!changed) {
            return null;
        }
        var newGlyf = glyphs.toByteArray();
        var newHead = head.clone();
        put32(newHead, 8, 0);
        if (!longOffsets && newGlyf.length / 2 > 0xffff) {
            longOffsets = true;
            put16(newHead, 50, 1);
        }
        tables.put("glyf", newGlyf);
        tables.put("loca", loca(newOffsets, longOffsets));
        tables.put("head", newHead);
        tables.put("hmtx", newHmtx);
        return write(version, tables);
    }

    /** Glyph indices that a composite glyph references. */
    private static java.util.List<Integer> components(byte[] glyf, int start, int end) {
        var result = new java.util.ArrayList<Integer>();
        var position = start + 10;
        int flags;
        do {
            require(position + 4 <= end);
            flags = u16(glyf, position);
            result.add(u16(glyf, position + 2));
            position += 4 + ((flags & ARG_1_AND_2_ARE_WORDS) != 0 ? 4 : 2);
            if ((flags & WE_HAVE_A_SCALE) != 0) {
                position += 2;
            } else if ((flags & WE_HAVE_AN_X_AND_Y_SCALE) != 0) {
                position += 4;
            } else if ((flags & WE_HAVE_A_TWO_BY_TWO) != 0) {
                position += 8;
            }
            require(position <= end);
        } while ((flags & MORE_COMPONENTS) != 0);
        return result;
    }

    /** The table bodies of a plain TrueType program, or null for CFF outlines and collections. */
    private static @Nullable TreeMap<String, byte[]> tables(byte[] font) {
        var version = u32(font, 0);
        if (version != 0x00010000 && version != 0x74727565) { // 'true'
            return null;
        }
        var count = u16(font, 4);
        require(count > 0 && 12 + 16L * count <= font.length);
        var tables = new TreeMap<String, byte[]>();
        for (int index = 0; index < count; index++) {
            var record = 12 + 16 * index;
            var tag = new String(font, record, 4, StandardCharsets.ISO_8859_1);
            var offset = u32(font, record + 8);
            var length = u32(font, record + 12);
            require(offset >= 0 && length >= 0 && offset + length <= font.length && !tables.containsKey(tag));
            tables.put(tag, Arrays.copyOfRange(font, (int) offset, (int) (offset + length)));
        }
        var head = tables.get("head");
        var maxp = tables.get("maxp");
        require(head != null && head.length >= 54 && maxp != null && maxp.length >= 6
            && tables.get("glyf") != null && tables.get("loca") != null
            && (s16(head, 50) == 0 || s16(head, 50) == 1));
        return tables;
    }

    private static long[] offsets(TreeMap<String, byte[]> tables, int glyphCount) {
        var glyf = tables.get("glyf");
        var loca = tables.get("loca");
        var longOffsets = s16(tables.get("head"), 50) == 1;
        var offsets = new long[glyphCount + 1];
        for (int index = 0; index <= glyphCount; index++) {
            offsets[index] = longOffsets ? u32(loca, index * 4) : 2L * u16(loca, index * 2);
            require(offsets[index] <= glyf.length && (index == 0 || offsets[index] >= offsets[index - 1]));
        }
        return offsets;
    }

    private static byte[] loca(long[] offsets, boolean longOffsets) {
        var loca = new byte[offsets.length * (longOffsets ? 4 : 2)];
        for (int index = 0; index < offsets.length; index++) {
            if (longOffsets) {
                put32(loca, index * 4, offsets[index]);
            } else {
                put16(loca, index * 2, (int) (offsets[index] / 2));
            }
        }
        return loca;
    }

    private static byte @Nullable [] rewrite(byte[] font) {
        var version = u32(font, 0);
        var tables = tables(font);
        if (tables == null) {
            return null;
        }
        var head = tables.get("head");
        var maxp = tables.get("maxp");
        var glyf = tables.get("glyf");
        var glyphCount = u16(maxp, 4);
        var longOffsets = s16(head, 50) == 1;
        var offsets = offsets(tables, glyphCount);

        var hinted = tables.keySet().stream().anyMatch(REMOVED_TABLES::contains);
        var glyphs = new ByteArrayOutputStream(glyf.length);
        var newOffsets = new long[glyphCount + 1];
        for (int index = 0; index < glyphCount; index++) {
            newOffsets[index] = glyphs.size();
            var glyph = Arrays.copyOfRange(glyf, (int) offsets[index], (int) offsets[index + 1]);
            var stripped = glyph.length == 0 ? glyph : withoutInstructions(glyph);
            hinted |= stripped.length != glyph.length;
            glyphs.writeBytes(stripped);
            while (glyphs.size() % (longOffsets ? 4 : 2) != 0) {
                glyphs.write(0);
            }
        }
        newOffsets[glyphCount] = glyphs.size();
        if (!hinted) {
            return null;
        }
        var newGlyf = glyphs.toByteArray();
        if (!longOffsets && newGlyf.length / 2 > 0xffff) {
            longOffsets = true;
        }
        var newLoca = loca(newOffsets, longOffsets);
        var newHead = head.clone();
        put16(newHead, 50, longOffsets ? 1 : 0);
        put32(newHead, 8, 0);
        var newMaxp = maxp.clone();
        if (u32(maxp, 0) == 0x00010000 && maxp.length >= 32) {
            put16(newMaxp, 14, 1);
            for (var field : new int[] {16, 18, 20, 22, 24, 26}) {
                put16(newMaxp, field, 0);
            }
        }
        tables.keySet().removeAll(REMOVED_TABLES);
        tables.put("glyf", newGlyf);
        tables.put("loca", newLoca);
        tables.put("head", newHead);
        tables.put("maxp", newMaxp);
        return write(version, tables);
    }

    private static byte[] withoutInstructions(byte[] glyph) {
        require(glyph.length >= 10);
        var contours = s16(glyph, 0);
        if (contours >= 0) {
            var lengthAt = 10 + 2 * contours;
            require(lengthAt + 2 <= glyph.length);
            var instructions = u16(glyph, lengthAt);
            require(lengthAt + 2 + instructions <= glyph.length);
            if (instructions == 0) {
                return glyph;
            }
            var points = contours == 0 ? 0 : u16(glyph, lengthAt - 2) + 1;
            var dataStart = lengthAt + 2 + instructions;
            var dataEnd = pointDataEnd(glyph, dataStart, points);
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
            require(position + 4 <= copy.length);
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
            require(position <= copy.length);
        } while ((flags & MORE_COMPONENTS) != 0);
        if (!withInstructions) {
            return glyph;
        }
        require(position + 2 <= copy.length);
        var instructions = u16(copy, position);
        require(position + 2 + instructions <= copy.length);
        return Arrays.copyOf(copy, position);
    }

    /** End of the flags and coordinates of a simple glyph, so stale padding is not carried over. */
    private static int pointDataEnd(byte[] glyph, int start, int points) {
        var flags = new int[points];
        var position = start;
        for (int point = 0; point < points; ) {
            require(position < glyph.length);
            var flag = glyph[position++] & 0xff;
            var repeat = 0;
            if ((flag & 0x08) != 0) {
                require(position < glyph.length);
                repeat = glyph[position++] & 0xff;
            }
            for (int copy = 0; copy <= repeat; copy++) {
                require(point < points);
                flags[point++] = flag;
            }
        }
        for (var flag : flags) {
            position += (flag & 0x02) != 0 ? 1 : (flag & 0x10) != 0 ? 0 : 2;
        }
        for (var flag : flags) {
            position += (flag & 0x04) != 0 ? 1 : (flag & 0x20) != 0 ? 0 : 2;
        }
        require(position <= glyph.length);
        return position;
    }

    private static byte[] write(long version, TreeMap<String, byte[]> tables) {
        var count = tables.size();
        var power = Integer.highestOneBit(count);
        var output = new ByteArrayOutputStream();
        var header = new byte[12 + 16 * count];
        put32(header, 0, version);
        put16(header, 4, count);
        put16(header, 6, power * 16);
        put16(header, 8, Integer.numberOfTrailingZeros(power));
        put16(header, 10, count * 16 - power * 16);
        var offset = (long) header.length;
        var index = 0;
        var body = new ByteArrayOutputStream();
        var headOffset = -1L;
        for (var entry : tables.entrySet()) {
            var data = entry.getValue();
            var record = 12 + 16 * index++;
            System.arraycopy(entry.getKey().getBytes(StandardCharsets.ISO_8859_1), 0, header, record, 4);
            put32(header, record + 4, checksum(data));
            put32(header, record + 8, offset);
            put32(header, record + 12, data.length);
            if (entry.getKey().equals("head")) {
                headOffset = offset;
            }
            body.writeBytes(data);
            var padding = (4 - data.length % 4) % 4;
            body.write(new byte[padding], 0, padding);
            offset += data.length + padding;
        }
        output.writeBytes(header);
        output.writeBytes(body.toByteArray());
        var result = output.toByteArray();
        put32(result, (int) headOffset + 8, (0xB1B0AFBAL - checksum(result)) & 0xffffffffL);
        return result;
    }

    private static long checksum(byte[] data) {
        long sum = 0;
        for (int index = 0; index < data.length; index += 4) {
            long word = 0;
            for (int part = 0; part < 4; part++) {
                word = word << 8 | (index + part < data.length ? data[index + part] & 0xff : 0);
            }
            sum = sum + word & 0xffffffffL;
        }
        return sum;
    }

    private static int u16(byte[] data, int offset) {
        return (data[offset] & 0xff) << 8 | data[offset + 1] & 0xff;
    }

    private static int s16(byte[] data, int offset) {
        return (short) u16(data, offset);
    }

    private static long u32(byte[] data, int offset) {
        return (long) u16(data, offset) << 16 | u16(data, offset + 2);
    }

    private static void put16(byte[] data, int offset, int value) {
        data[offset] = (byte) (value >>> 8);
        data[offset + 1] = (byte) value;
    }

    private static void put32(byte[] data, int offset, long value) {
        put16(data, offset, (int) (value >>> 16) & 0xffff);
        put16(data, offset + 2, (int) value & 0xffff);
    }

    private static void require(boolean condition) {
        if (!condition) {
            throw new IllegalArgumentException("Unsupported TrueType program");
        }
    }
}
