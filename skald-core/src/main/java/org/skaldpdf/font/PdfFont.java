package org.skaldpdf.font;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Immutable embedded OpenType font. Font instances are safe to share across documents. */
public final class PdfFont {
    private final TrueTypeFont program;
    private final FontWeight weight;

    PdfFont(TrueTypeFont program, FontWeight weight) {
        this.program = program;
        this.weight = weight;
    }

    public FontWeight weight() {
        return weight;
    }

    public boolean bold() {
        return weight == FontWeight.BOLD;
    }

    public float getWidth(String text, float fontSize) {
        return glyphRun(text).advance() * fontSize / 1_000f;
    }

    public float ascent(float fontSize) {
        var metrics = metrics();
        return metrics.pdfUnit(metrics.ascent()) * fontSize / 1_000f;
    }

    public float descent(float fontSize) {
        var metrics = metrics();
        return metrics.pdfUnit(metrics.descent()) * fontSize / 1_000f;
    }

    public boolean supports(int codePoint) {
        return program.glyph(codePoint) != 0 || codePoint == 0;
    }

    public GlyphRun glyphRun(String text) {
        var codePoints = text.codePoints().toArray();
        var glyphs = new int[codePoints.length];
        var advance = 0;
        for (int index = 0; index < codePoints.length; index++) {
            glyphs[index] = program.glyph(codePoints[index]);
            advance += program.pdfWidth(glyphs[index]);
        }
        return new GlyphRun(glyphs, codePoints, advance);
    }

    public byte[] subsetProgram(Set<Integer> glyphs) {
        return program.subset(glyphs);
    }

    public Map<Integer, Integer> widths(Set<Integer> glyphs) {
        var widths = new LinkedHashMap<Integer, Integer>();
        glyphs.stream().sorted().forEach(glyph -> widths.put(glyph, program.pdfWidth(glyph)));
        return Map.copyOf(widths);
    }

    public Metrics metrics() {
        return program.metrics();
    }

    public String postScriptName() {
        return program.postScriptName();
    }

    public record GlyphRun(int[] glyphs, int[] codePoints, int advance) {
        public GlyphRun {
            glyphs = glyphs.clone();
            codePoints = codePoints.clone();
        }

        @Override
        public int[] glyphs() {
            return glyphs.clone();
        }

        @Override
        public int[] codePoints() {
            return codePoints.clone();
        }
    }

    public record Metrics(int unitsPerEm, int xMin, int yMin, int xMax, int yMax,
                          int ascent, int descent, int capHeight, float italicAngle,
                          boolean fixedPitch) {
        public int pdfUnit(int value) {
            return Math.round(value * 1_000f / unitsPerEm);
        }
    }
}
