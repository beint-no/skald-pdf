package org.skaldpdf;

import org.apache.pdfbox.text.PDFTextStripper;
import org.skaldpdf.colors.DeviceRgb;
import org.skaldpdf.fonts.SkaldSans;
import org.skaldpdf.geom.PageSize;
import org.skaldpdf.geom.Rectangle;
import org.skaldpdf.layout.Canvas;
import org.skaldpdf.layout.Document;
import org.skaldpdf.layout.element.Cell;
import org.skaldpdf.layout.element.Div;
import org.skaldpdf.layout.element.ListBlock;
import org.skaldpdf.layout.element.Paragraph;
import org.skaldpdf.layout.element.Table;
import org.skaldpdf.layout.properties.UnitValue;
import org.skaldpdf.pdf.PdfDocument;
import org.skaldpdf.pdf.PdfWriter;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LayoutPaginationTest {
    @Test
    void splitsAnOversizedParagraphWithoutLosingText() throws Exception {
        var marker = "final-paragraph-marker";
        var body = ("A deliberately long contractual paragraph with Unicode æøå and predictable wrapping. ")
            .repeat(230) + marker;
        var bytes = render(document -> document.add(new Paragraph(body).setFontSize(11).setPadding(8)
            .setBackgroundColor(new DeviceRgb(245, 248, 250))));

        try (var parsed = PdfTestSupport.load(bytes)) {
            assertTrue(parsed.getNumberOfPages() >= 3);
        }
        assertTrue(PdfTestSupport.text(bytes).contains(marker));
    }

    @Test
    void splitsADeepTableRowAndRepeatsItsHeader() throws Exception {
        var marker = "final-cell-marker";
        var table = new Table(UnitValue.createPercentArray(new float[] {1, 4})).useAllAvailableWidth();
        table.addHeaderCell(new Cell().add(new Paragraph("Code").bold()));
        table.addHeaderCell(new Cell().add(new Paragraph("Description").bold()));
        table.addCell(new Cell().add(new Paragraph("A-01")));
        table.addCell(new Cell().add(new Paragraph(
            "A long product description intended to exercise row fragmentation. ".repeat(260) + marker
        )));

        var bytes = render(document -> document.add(table));
        var text = PdfTestSupport.text(bytes);
        try (var parsed = PdfTestSupport.load(bytes)) {
            assertTrue(parsed.getNumberOfPages() >= 3);
            assertEquals(parsed.getNumberOfPages(), text.split("Description", -1).length - 1);
        }
        assertTrue(text.contains(marker));
    }

    @Test
    void keepsTrailingPaddingOnTheLastPageOfASplitRow() throws Exception {
        var lines = java.util.stream.IntStream.range(0, 111)
            .mapToObj(i -> "gateway-" + i + "-end").toList();
        var table = new Table(new float[] {1}).useAllAvailableWidth();
        table.addHeaderCell("Gateway");
        table.addCell(new Cell().setPadding(2).add(new Paragraph(String.join("\n", lines))
            .setFontSize(7.5f).setMultipliedLeading(1)));

        var bytes = render(document -> document.add(table));
        try (var parsed = PdfTestSupport.load(bytes)) {
            assertEquals(2, parsed.getNumberOfPages());
        }
        var text = PdfTestSupport.text(bytes);
        assertEquals(lines, text.lines().map(String::strip).filter(line -> line.startsWith("gateway-")).toList());
    }

    @Test
    void splitsParagraphAcrossPagesWhenKeepTogetherIsFalseAndJumpsWholeWhenTrue() throws Exception {
        var lines = IntStream.range(1, 11).mapToObj(i -> "para-line-" + i).toList();
        var body = String.join("\n", lines);
        var smallPage = new PageSize(300, 240);

        var splitPdf = render(smallPage, document -> {
            document.add(new Paragraph("filler").setFontSize(10).setHeight(110));
            document.add(new Paragraph(body).setFontSize(10).setMultipliedLeading(1.2f));
        });
        try (var parsed = PdfTestSupport.load(splitPdf)) {
            assertEquals(2, parsed.getNumberOfPages());
            var page1 = pageText(parsed, 1);
            var page2 = pageText(parsed, 2);
            assertTrue(page1.contains("para-line-1"), "Default keepTogether(false) should start on page 1:\n" + page1);
            assertTrue(page2.contains("para-line-10"), "Remainder should continue on page 2:\n" + page2);
            assertFalse(page1.contains("para-line-10"), "Should actually split across pages:\n" + page1);
        }

        var keptPdf = render(smallPage, document -> {
            document.add(new Paragraph("filler").setFontSize(10).setHeight(110));
            document.add(new Paragraph(body).setFontSize(10).setMultipliedLeading(1.2f).setKeepTogether(true));
        });
        try (var parsed = PdfTestSupport.load(keptPdf)) {
            assertEquals(2, parsed.getNumberOfPages());
            var page1 = pageText(parsed, 1);
            var page2 = pageText(parsed, 2);
            assertFalse(page1.contains("para-line-1"), "keepTogether(true) should move whole paragraph off page 1:\n" + page1);
            assertTrue(page2.contains("para-line-1") && page2.contains("para-line-10"),
                "keepTogether(true) should render whole paragraph on page 2:\n" + page2);
        }
    }

    @Test
    void keepsThreeLineParagraphTogetherInsteadOfLeavingAnOrphanOrWidow() throws Exception {
        var smallPage = new PageSize(300, 200);
        // Content height = 200 - 72 = 128pt. Filler takes 100pt, leaving 28pt (fits 2 of 3 12pt lines).
        // Widow avoidance trims 2 -> 1 line (leaving leftover == 2), then orphan avoidance moves all 3 lines to page 2.
        var bytes = render(smallPage, document -> {
            document.add(new Paragraph("filler").setFontSize(10).setHeight(100));
            document.add(new Paragraph("tri-1\ntri-2\ntri-3").setFontSize(10).setMultipliedLeading(1.2f));
        });
        try (var parsed = PdfTestSupport.load(bytes)) {
            assertEquals(2, parsed.getNumberOfPages());
            var page1 = pageText(parsed, 1);
            var page2 = pageText(parsed, 2);
            assertFalse(page1.contains("tri-1"), "Should not leave a single orphan line on page 1:\n" + page1);
            assertTrue(page2.contains("tri-1") && page2.contains("tri-2") && page2.contains("tri-3"),
                "All three lines should move cleanly to page 2:\n" + page2);
        }
    }

    @Test
    void estimateRespectsExplicitParagraphWidthInsideKeepWithNextAndDiv() throws Exception {
        var smallPage = new PageSize(300, 180);
        // Content height = 108pt. Filler takes 60pt, leaving 48pt.
        // The narrowed paragraph (width 60pt) wraps to ~6 lines (> 60pt), so a decorated Div must move to page 2.
        var narrow = new Paragraph("one two three four five six seven eight nine ten")
            .setFontSize(10)
            .setWidth(UnitValue.createPointValue(60));
        var bytes = render(smallPage, document -> {
            document.add(new Paragraph("filler").setFontSize(10).setHeight(60));
            document.add(new Div().setBackgroundColor(new DeviceRgb(240, 240, 240)).add(narrow));
        });
        try (var parsed = PdfTestSupport.load(bytes)) {
            assertEquals(2, parsed.getNumberOfPages());
            var page2 = pageText(parsed, 2);
            assertTrue(page2.contains("one") && page2.contains("ten"),
                "Decorated Div containing narrowed paragraph should estimate wrapped height and jump to page 2:\n" + page2);
        }
    }

    @Test
    void splitsMixedFontSizeTableRowAcrossPagesWithoutDroppingAnyLine() throws Exception {
        var leftLines = IntStream.range(0, 90).mapToObj(i -> "LEFT-" + i + "-END").toList();
        var rightLines = IntStream.range(0, 120).mapToObj(i -> "RIGHT-" + i + "-END").toList();
        var table = new Table(new float[] {1, 1}).useAllAvailableWidth();
        table.addHeaderCell("Left");
        table.addHeaderCell("Right");
        table.addCell(new Cell().setPadding(3).add(new Paragraph(String.join("\n", leftLines))
            .setFontSize(10f).setMultipliedLeading(1.25f)));
        table.addCell(new Cell().setPadding(3).add(new Paragraph(String.join("\n", rightLines))
            .setFontSize(7.5f).setMultipliedLeading(1.15f)));

        var bytes = render(document -> document.add(table));
        try (var parsed = PdfTestSupport.load(bytes)) {
            assertTrue(parsed.getNumberOfPages() >= 2);
        }
        var extracted = PdfTestSupport.text(bytes);
        for (var expected : leftLines) {
            assertEquals(1, occurrences(extracted, expected), "Missing or duplicated left line: " + expected);
        }
        for (var expected : rightLines) {
            assertEquals(1, occurrences(extracted, expected), "Missing or duplicated right line: " + expected);
        }
    }

    @Test
    void canvasOverlayHonorsFontSettingsAndNeverSpawnsExtraPagesForTablesOrLists() throws Exception {
        var output = new ByteArrayOutputStream();
        try (var pdf = new PdfDocument(new PdfWriter(output))) {
            var page = pdf.addNewPage(new PageSize(300, 200));
            try (var canvas = new Canvas(page, new Rectangle(20, 20, 260, 160))) {
                canvas.setFont(SkaldSans.bold())
                    .setFontSize(17f)
                    .setFontColor(new DeviceRgb(200, 10, 20))
                    .add(new Paragraph("Styled overlay"));
                var tallTable = new Table(1).useAllAvailableWidth();
                for (int i = 0; i < 30; i++) {
                    tallTable.addCell("row-" + i);
                }
                canvas.add(tallTable);
                var tallList = new ListBlock();
                for (int i = 0; i < 30; i++) {
                    tallList.add("item-" + i);
                }
                canvas.add(tallList);
            }
        }
        var bytes = output.toByteArray();
        try (var parsed = PdfTestSupport.load(bytes);
             var contents = parsed.getPage(0).getContents()) {
            assertEquals(1, parsed.getNumberOfPages(), "Canvas overlays must never spawn extra pages");
            var stream = new String(contents.readAllBytes(), StandardCharsets.ISO_8859_1);
            assertTrue(stream.contains(" 17 Tf"), "Canvas fontSize should apply to added Paragraph:\n" + stream);
            assertTrue(stream.contains("0.78431 0.03922 0.07843 rg"),
                "Canvas fontColor should apply to added Paragraph:\n" + stream);
        }
        assertTrue(PdfTestSupport.text(bytes).contains("Styled overlay"));
    }

    @Test
    void freezesDefaultsOnceStreamingLayoutStarts() {
        var output = new ByteArrayOutputStream();
        try (var pdf = new PdfDocument(new PdfWriter(output));
             var document = new Document(pdf)) {
            document.add(new Paragraph("first"));
            document.add(new Paragraph("second"));
            assertThrows(IllegalStateException.class, () -> document.setFontSize(10));
        }
    }

    private static int occurrences(String haystack, String needle) {
        var count = 0;
        var from = 0;
        while ((from = haystack.indexOf(needle, from)) >= 0) {
            count++;
            from += needle.length();
        }
        return count;
    }

    private static String pageText(org.apache.pdfbox.pdmodel.PDDocument document, int pageNumber) throws Exception {
        var stripper = new PDFTextStripper();
        stripper.setStartPage(pageNumber);
        stripper.setEndPage(pageNumber);
        return stripper.getText(document);
    }

    private static byte[] render(java.util.function.Consumer<Document> content) {
        return render(PageSize.A4, content);
    }

    private static byte[] render(PageSize pageSize, java.util.function.Consumer<Document> content) {
        var output = new ByteArrayOutputStream();
        try (var pdf = new PdfDocument(new PdfWriter(output));
             var document = new Document(pdf, pageSize)) {
            document.setMargins(36, 36, 36, 36);
            content.accept(document);
        }
        return output.toByteArray();
    }
}
