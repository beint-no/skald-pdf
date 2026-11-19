package org.skaldpdf;

import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.skaldpdf.geom.PageSize;
import org.skaldpdf.layout.element.Cell;
import org.skaldpdf.layout.element.Div;
import org.skaldpdf.layout.element.Paragraph;
import org.skaldpdf.layout.element.Table;
import org.skaldpdf.layout.properties.OverflowWrap;
import org.skaldpdf.layout.properties.UnitValue;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OverflowWrapTest {
    @Test
    void keepsNormalWordsIntactAndBreaksOnlyOversizedTokensUnderAnywhere() throws Exception {
        var longToken = "abcdefghijklmnopqrstuvwxyz0123456789";
        var bytes = Pdf.create(new PageSize(200, 300), document -> {
            document.setMargins(20);
            document.add(new Paragraph("Hello world " + longToken + " done completed")
                .setFontSize(10)
                .setWidth(UnitValue.createPointValue(80))
                .setOverflowWrap(OverflowWrap.ANYWHERE));
        });
        var lines = nonBlankLines(bytes);
        assertEquals("Hello world", lines.getFirst(), "Words fitting on a line must not be split: " + lines);
        assertEquals("completed", lines.getLast(), "Normal word exceeding remaining space must wrap intact: " + lines);
        var joined = String.join("", lines);
        assertTrue(joined.contains(longToken), "Oversized token must break cleanly without losing characters: " + lines);
        assertTrue(lines.stream().anyMatch(line -> line.endsWith(" done")),
            "Trailing short word should stay intact: " + lines);
        assertTrue(lines.size() >= 5, "Oversized token should wrap across multiple lines: " + lines);
    }

    @Test
    void inheritsOverflowWrapFromDivCellAndTableAndAllowsChildOverride() throws Exception {
        var token = "0123456789abcdef0123456789abcdef";
        var bytes = Pdf.create(new PageSize(240, 400), document -> {
            document.setMargins(20);
            document.add(new Div()
                .setWidth(UnitValue.createPointValue(70))
                .setOverflowWrap(OverflowWrap.ANYWHERE)
                .add(new Paragraph(token).setFontSize(10))
                .add(new Paragraph(token).setFontSize(10).setOverflowWrap(OverflowWrap.NORMAL)));
        });
        var lines = nonBlankLines(bytes);
        // First paragraph inherits ANYWHERE (wrapped across multiple lines); second overrides NORMAL (stays on 1 line).
        assertEquals(token, lines.getLast(), "Child override to NORMAL should keep token on one line: " + lines);
        var wrapped = String.join("", lines.subList(0, lines.size() - 1));
        assertEquals(token, wrapped, "Inherited ANYWHERE should split token across lines: " + lines);
        assertTrue(lines.size() >= 4, "Inherited ANYWHERE should wrap into multiple lines: " + lines);
    }

    @Test
    void narrowColumnWithAnywhereDoesNotCrushSiblingColumns() throws Exception {
        var hash = "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08";
        var table = new Table(UnitValue.createPercentArray(new float[] {25, 75}))
            .useAllAvailableWidth()
            .setOverflowWrap(OverflowWrap.ANYWHERE);
        table.addCell(new Cell().add(new Paragraph(hash).setFontSize(9)));
        table.addCell(new Cell().add(new Paragraph("Standard invoice description that stays readable").setFontSize(9)));

        var bytes = Pdf.create(new PageSize(320, 400), document -> {
            document.setMargins(20);
            document.add(table);
        });
        var text = PdfTestSupport.text(bytes);
        // Sibling column has 75% of 280pt = 210pt, easily fitting "Standard invoice description" on one line.
        assertTrue(text.contains("Standard invoice description"),
            "Sibling column should not be crushed by the long token in col 1:\n" + text);
        // Every character of the hash appears without loss.
        var compact = text.replaceAll("\\s+", "");
        assertTrue(compact.contains(hash), "Wrapped hash lost characters:\n" + text);
    }

    @Test
    void treatsCrLfAndStandaloneCrAsCleanLineBreaksWithoutNotdefGlyphs() throws Exception {
        var bytes = Pdf.create(PageSize.A4, document -> {
            document.setMargins(36);
            document.add(new Paragraph("Alpha\r\nBeta\rGamma\nDelta").setFontSize(11));
        });
        var lines = nonBlankLines(bytes);
        assertEquals(List.of("Alpha", "Beta", "Gamma", "Delta"), lines);
        try (var parsed = PdfTestSupport.load(bytes);
             var contents = parsed.getPage(0).getContents()) {
            var stream = new String(contents.readAllBytes(), StandardCharsets.ISO_8859_1);
            assertFalse(stream.contains("<0000>"), "Must never emit .notdef glyph for CR:\n" + stream);
        }
    }

    @Test
    void treatsZeroWidthSpaceAsBreakOpportunityWithoutEmittingGlyphs() throws Exception {
        var narrowBytes = Pdf.create(new PageSize(180, 300), document -> {
            document.setMargins(20);
            document.add(new Paragraph("nordic\u200Binvoice\u200Bnumber")
                .setFontSize(11)
                .setWidth(UnitValue.createPointValue(55)));
        });
        assertEquals(List.of("nordic", "invoice", "number"), nonBlankLines(narrowBytes));

        var wideBytes = Pdf.create(PageSize.A4, document -> {
            document.setMargins(36);
            document.add(new Paragraph("nordic\u200Binvoice\u200Bnumber").setFontSize(11));
        });
        var wideLines = nonBlankLines(wideBytes);
        assertEquals(List.of("nordicinvoicenumber"), wideLines);
        try (var parsed = PdfTestSupport.load(wideBytes);
             var contents = parsed.getPage(0).getContents()) {
            var stream = new String(contents.readAllBytes(), StandardCharsets.ISO_8859_1);
            assertFalse(stream.contains("<0000>"), "Must never emit .notdef glyph for U+200B:\n" + stream);
        }
    }

    private static List<String> nonBlankLines(byte[] pdf) throws Exception {
        try (var doc = PdfTestSupport.load(pdf)) {
            return new PDFTextStripper().getText(doc).lines()
                .map(String::strip)
                .filter(line -> !line.isEmpty())
                .toList();
        }
    }
}
