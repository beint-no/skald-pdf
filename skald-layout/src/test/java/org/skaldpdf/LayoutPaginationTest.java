package org.skaldpdf;

import org.skaldpdf.colors.DeviceRgb;
import org.skaldpdf.geom.PageSize;
import org.skaldpdf.layout.Document;
import org.skaldpdf.layout.element.Cell;
import org.skaldpdf.layout.element.Paragraph;
import org.skaldpdf.layout.element.Table;
import org.skaldpdf.layout.properties.UnitValue;
import org.skaldpdf.pdf.PdfDocument;
import org.skaldpdf.pdf.PdfWriter;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    void freezesDefaultsOnceStreamingLayoutStarts() {
        var output = new ByteArrayOutputStream();
        try (var pdf = new PdfDocument(new PdfWriter(output));
             var document = new Document(pdf)) {
            document.add(new Paragraph("first"));
            document.add(new Paragraph("second"));
            assertThrows(IllegalStateException.class, () -> document.setFontSize(10));
        }
    }

    private static byte[] render(java.util.function.Consumer<Document> content) {
        var output = new ByteArrayOutputStream();
        try (var pdf = new PdfDocument(new PdfWriter(output));
             var document = new Document(pdf, PageSize.A4)) {
            document.setMargins(36, 36, 36, 36);
            content.accept(document);
        }
        return output.toByteArray();
    }
}
