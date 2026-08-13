package org.skaldpdf.font;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrueTypeSubsetTest {
    @Test
    void dropsLayoutTablesAndKeepsACompactGlyphSet() {
        var font = PdfFontFactory.regular();
        var run = font.glyphRun("Invoice 2026 NOK 1 250.00");
        var glyphs = new LinkedHashSet<Integer>();
        for (var glyph : run.glyphs()) {
            glyphs.add(glyph);
        }
        var subset = font.subsetProgram(glyphs);
        assertTrue(subset.length < 40_000, "compact subset should be well under the 200KB source face: " + subset.length);
        assertTrue(tableNames(subset).contains("glyf"));
        assertTrue(tableNames(subset).contains("cmap"));
        assertTrue(tableNames(subset).contains("post"));
        assertFalse(tableNames(subset).contains("GPOS"));
        assertFalse(tableNames(subset).contains("GSUB"));
        assertFalse(tableNames(subset).contains("GDEF"));
        assertFalse(tableNames(subset).contains("DSIG"));
        assertFalse(tableNames(subset).contains("meta"));
    }

    private static Set<String> tableNames(byte[] font) {
        var buffer = ByteBuffer.wrap(font).order(ByteOrder.BIG_ENDIAN);
        var count = Short.toUnsignedInt(buffer.getShort(4));
        var names = new LinkedHashSet<String>();
        for (int index = 0; index < count; index++) {
            var offset = 12 + index * 16;
            names.add(new String(font, offset, 4, StandardCharsets.US_ASCII));
        }
        return names;
    }
}
