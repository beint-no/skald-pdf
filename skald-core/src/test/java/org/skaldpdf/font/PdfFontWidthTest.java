package org.skaldpdf.font;

import org.junit.jupiter.api.Test;
import org.skaldpdf.fonts.SkaldSans;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PdfFontWidthTest {
    @Test
    void measurementsMatchTheGlyphsWrittenForUnicodeAndMissingCharacters() {
        var strings = List.of("", "Invoice 2026-1001", "Blåbær, størrelse Ø",
            "e\u0301", "A\uD83D\uDE00B", "\uD800", "\uDC00", "\u0000");
        for (var weight : FontWeight.values()) {
            var font = SkaldSans.create(weight);
            for (var text : strings) {
                for (var size : new float[] { 0, 7, 10.5f, 72 }) {
                    assertEquals(font.glyphRun(text).advance() * size / 1_000f,
                        font.getWidth(text, size), weight + ": " + text);
                }
            }
        }
    }
}
