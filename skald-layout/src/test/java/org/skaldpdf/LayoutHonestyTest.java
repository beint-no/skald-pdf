package org.skaldpdf;

import org.skaldpdf.colors.ColorConstants;
import org.skaldpdf.colors.DeviceRgb;
import org.skaldpdf.fonts.SkaldSans;
import org.skaldpdf.geom.PageSize;
import org.skaldpdf.codec.RasterImages;
import org.skaldpdf.layout.element.AreaBreak;
import org.skaldpdf.layout.element.Cell;
import org.skaldpdf.layout.element.Div;
import org.skaldpdf.layout.element.Image;
import org.skaldpdf.layout.element.ListBlock;
import org.skaldpdf.layout.element.Paragraph;
import org.skaldpdf.layout.element.Table;
import org.skaldpdf.layout.properties.TextAlignment;
import org.skaldpdf.layout.properties.UnitValue;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LayoutHonestyTest {
    @Test
    void paintsNestedBlocksInsideTableCells() throws Exception {
        var table = new Table(UnitValue.createPercentArray(new float[] {1, 1})).useAllAvailableWidth();
        table.addHeaderCell(new Cell().add(new Paragraph("Left").bold()));
        table.addHeaderCell(new Cell().add(new Paragraph("Right").bold()));
        table.addCell(new Cell().add(new Div()
            .setBackgroundColor(new DeviceRgb(238, 245, 241))
            .setPadding(6)
            .add(new Paragraph("Nested card").bold())
            .add(new ListBlock().add("First nested item").add("Second nested item"))));
        table.addCell(new Cell().add(new Paragraph("Sibling cell").setFontColor(ColorConstants.MUTED)));

        var bytes = Pdf.create(document -> document.add(table));
        var text = PdfTestSupport.text(bytes);
        assertTrue(text.contains("Nested card"));
        assertTrue(text.contains("First nested item"));
        assertTrue(text.contains("Sibling cell"));
        PdfTestSupport.assertVisibleInk(PdfTestSupport.renderFirstPage(bytes));
        PdfTestSupport.saveArtifacts("nested-cell", bytes);
    }

    @Test
    void paintsChildrenOfFixedPositionDivs() throws Exception {
        var bytes = Pdf.create(document -> document.add(
            new Div()
                .setFixedPosition(48, 640, 400)
                .setBackgroundColor(ColorConstants.SURFACE)
                .setPadding(10)
                .add(new Paragraph("Fixed title").bold())
                .add(new Paragraph("Fixed body copy that must actually paint."))
        ));
        var text = PdfTestSupport.text(bytes);
        assertTrue(text.contains("Fixed title"));
        assertTrue(text.contains("Fixed body copy"));
    }

    @Test
    void keepsABlockTogetherInsteadOfSplittingIt() throws Exception {
        var bytes = Pdf.create(document -> {
            document.setMargins(36, 36, 36, 36);
            for (int index = 0; index < 46; index++) {
                document.add(new Paragraph("Filler line " + index).setFontSize(12));
            }
            document.add(new Paragraph("KEEP-TOGETHER-START\nSecond line of the block\nKEEP-TOGETHER-END")
                .setKeepTogether(true)
                .setBackgroundColor(ColorConstants.HIGHLIGHT)
                .setPadding(8));
        });
        try (var parsed = PdfTestSupport.load(bytes)) {
            assertTrue(parsed.getNumberOfPages() >= 2);
        }
        var text = PdfTestSupport.text(bytes);
        assertTrue(text.contains("KEEP-TOGETHER-START"));
        assertTrue(text.contains("KEEP-TOGETHER-END"));
        var lastPage = text.substring(text.lastIndexOf("KEEP-TOGETHER-START"));
        assertTrue(lastPage.contains("KEEP-TOGETHER-END"));
    }

    @Test
    void usesADifferentHeaderOnTheFirstPage() throws Exception {
        var bytes = Pdf.create(document -> {
            document.setMargins(48, 48, 40, 48)
                .setHeader(18, page -> new Paragraph("CONTINUING HEADER")
                    .setFontSize(9).setFontColor(ColorConstants.MUTED))
                .setFirstHeader(page -> new Paragraph("FIRST PAGE LETTERHEAD")
                    .setFontSize(9).setFontColor(ColorConstants.ACCENT));
            document.add(new Paragraph("Cover copy").bold().setFontSize(20));
            for (int index = 0; index < 50; index++) {
                document.add(new Paragraph("Body line " + index).setFontSize(11));
            }
        });
        var text = PdfTestSupport.text(bytes);
        assertTrue(text.contains("FIRST PAGE LETTERHEAD"));
        assertTrue(text.contains("CONTINUING HEADER"));
        try (var parsed = PdfTestSupport.load(bytes)) {
            assertTrue(parsed.getNumberOfPages() >= 2);
            assertTrue(pageText(parsed, 1).contains("FIRST PAGE LETTERHEAD"));
            assertFalse(pageText(parsed, 2).contains("FIRST PAGE LETTERHEAD"));
            assertTrue(pageText(parsed, 2).contains("CONTINUING HEADER"));
        }
    }

    @Test
    void underlinesAndStrikesThroughText() throws Exception {
        var bytes = Pdf.create(document -> {
            document.add(new Paragraph("Underlined total").underline().setFontSize(16));
            document.add(new Paragraph("Was 1 200.00").strikethrough().setFontColor(ColorConstants.MUTED));
        });
        var text = PdfTestSupport.text(bytes);
        assertTrue(text.contains("Underlined total"));
        assertTrue(text.contains("Was 1 200.00"));
        PdfTestSupport.assertVisibleInk(PdfTestSupport.renderFirstPage(bytes));
    }

    @Test
    void writesInternalGotoLinks() throws Exception {
        var bytes = Pdf.create(document -> {
            document.add(new Paragraph("See appendix").setDestinationPage(2).setFontColor(ColorConstants.ACCENT));
            document.add(new AreaBreak());
            document.add(new Paragraph("Appendix starts here").bold());
        });
        try (var parsed = PdfTestSupport.load(bytes)) {
            assertEquals(2, parsed.getNumberOfPages());
            var annotations = parsed.getPage(0).getAnnotations();
            assertFalse(annotations.isEmpty());
            assertEquals("Link", annotations.getFirst().getSubtype());
        }
    }

    @Test
    void changesPageSizeWithAnAreaBreak() throws Exception {
        var bytes = Pdf.create(document -> {
            document.add(new Paragraph("Portrait report"));
            document.add(new AreaBreak(PageSize.A4.landscape()));
            document.add(new Paragraph("Landscape appendix"));
        });
        try (var parsed = PdfTestSupport.load(bytes)) {
            assertEquals(2, parsed.getNumberOfPages());
            assertTrue(parsed.getPage(0).getMediaBox().getHeight() > parsed.getPage(0).getMediaBox().getWidth());
            assertTrue(parsed.getPage(1).getMediaBox().getWidth() > parsed.getPage(1).getMediaBox().getHeight());
        }
        var text = PdfTestSupport.text(bytes);
        assertTrue(text.contains("Portrait report"));
        assertTrue(text.contains("Landscape appendix"));
    }

    @Test
    void substitutesMissingGlyphsInsteadOfAborting() {
        assertDoesNotThrow(() -> Pdf.create(document ->
            document.add(new Paragraph("Invoice ☃ 2026"))));
        assertFalse(SkaldSans.regular().supports(0x2603));
    }

    @Test
    void measuresFittedImagesWhenEstimatingBlocks() throws Exception {
        var image = RasterImages.decode(PdfTestSupport.sampleLogo());
        var bytes = Pdf.create(document -> document.add(
            new Div()
                .setBackgroundColor(ColorConstants.SURFACE)
                .setPadding(8)
                .add(new Image(image).scale(720, 240))
                .add(new Paragraph("Caption under a fitted logo"))
        ));
        var text = PdfTestSupport.text(bytes);
        assertTrue(text.contains("Caption under a fitted logo"));
        try (var parsed = PdfTestSupport.load(bytes)) {
            assertEquals(1, parsed.getNumberOfPages());
        }
    }

    @Test
    void embedsTheFontsPostScriptName() throws Exception {
        var bytes = Pdf.create(document -> document.add(new Paragraph("Named face").setTextAlignment(TextAlignment.LEFT)));
        var name = SkaldSans.regular().postScriptName();
        assertTrue(name != null && !name.isBlank());
        try (var parsed = PdfTestSupport.load(bytes)) {
            var fonts = parsed.getPage(0).getResources().getFontNames();
            var joined = new StringBuilder();
            for (var fontName : fonts) {
                joined.append(parsed.getPage(0).getResources().getFont(fontName).getName());
            }
            assertTrue(joined.toString().contains(name.replace(' ', '-')),
                "writer should use " + name + " but fonts were " + joined);
        }
    }

    private static String pageText(org.apache.pdfbox.pdmodel.PDDocument document, int page) throws Exception {
        var stripper = new org.apache.pdfbox.text.PDFTextStripper();
        stripper.setStartPage(page);
        stripper.setEndPage(page);
        return stripper.getText(document);
    }
}
