package org.skaldpdf;

import org.skaldpdf.colors.ColorConstants;
import org.skaldpdf.geom.PageSize;
import org.skaldpdf.layout.element.Cell;
import org.skaldpdf.layout.element.Paragraph;
import org.skaldpdf.layout.element.Table;
import org.skaldpdf.layout.properties.UnitValue;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TableFeaturesTest {
    @Test
    void paintsARowSpanningGroupCell() throws Exception {
        var table = new Table(3).useAllAvailableWidth();
        table.addHeaderCell("Group").addHeaderCell("Item").addHeaderCell("Qty");
        table.addCell(new Cell(1, 2).add(new Paragraph("Nordic").bold())
            .setBackgroundColor(ColorConstants.SURFACE));
        table.addCell("Bowl");
        table.addCell("4");
        table.addCell("Tray");
        table.addCell("2");
        var bytes = Pdf.create(document -> document.add(table));
        var text = PdfTestSupport.text(bytes);
        assertTrue(text.contains("Nordic"));
        assertTrue(text.contains("Bowl"));
        assertTrue(text.contains("Tray"));
        PdfTestSupport.saveArtifacts("rowspan-table", bytes);
    }

    @Test
    void strokesAUniformCellBorderAsOnePath() throws Exception {
        var table = new Table(2).useAllAvailableWidth();
        table.addRow("A", "B");
        table.addRow("C", "D");
        var bytes = Pdf.create(document -> document.add(table));
        try (var parsed = PdfTestSupport.load(bytes);
             var contents = parsed.getPage(0).getContents()) {
            var content = new String(contents.readAllBytes(), java.nio.charset.StandardCharsets.ISO_8859_1);
            assertEquals(4, content.split(" RG\n", -1).length - 1, "one stroke state per cell");
            assertEquals(4, content.split("\nS\nQ\n", -1).length - 1, "one stroke per cell");
            assertEquals(16, content.split(" l\n", -1).length - 1, "four sides per cell");
        }
    }

    @Test
    void repeatsFooterRowsWhenATableSplits() throws Exception {
        var table = new Table(2).useAllAvailableWidth();
        table.addHeaderCell("Line").addHeaderCell("Amount");
        for (int index = 1; index <= 40; index++) {
            table.addRow("Consulting " + index, "1 250.00");
        }
        table.addFooterCell(new Cell().add(new Paragraph("Total").bold())
            .setBackgroundColor(ColorConstants.ACCENT));
        table.addFooterCell(new Cell().add(new Paragraph("50 000.00").bold())
            .setBackgroundColor(ColorConstants.ACCENT));
        var bytes = Pdf.create(PageSize.A4, document -> {
            document.setMargins(36, 36, 36, 36);
            document.add(table);
        });
        var text = PdfTestSupport.text(bytes);
        try (var parsed = PdfTestSupport.load(bytes)) {
            assertTrue(parsed.getNumberOfPages() >= 2);
            assertEquals(parsed.getNumberOfPages(), text.split("Total", -1).length - 1);
        }
        PdfTestSupport.saveArtifacts("table-footer", bytes);
    }

    @Test
    void keepsShortHeaderWordsIntactAndDrawsHairlineRules() throws Exception {
        var table = new Table(UnitValue.createPercentArray(new float[] {22, 16, 8, 14, 14, 11, 15}))
            .useAllAvailableWidth();
        table.addHeaderRow("Beskrivelse", "Kommentar", "Antall", "Enhetspris", "Beløp", "MVA", "Beløp");
        table.addRow("Regnskapstjeneste august", "Kreditert", "8", "-1,250.00", "-10,000.00", "25 %", "-12,500.00");
        table.addRule(1.25f);
        table.addRow("Beløp", "", "", "", "", "", "NOK -12,500.00");
        table.addRule(1.25f);
        var bytes = Pdf.create(document -> {
            document.setMargins(40);
            document.add(table);
        });
        var text = PdfTestSupport.text(bytes);
        assertTrue(text.contains("Antall"));
        assertTrue(!text.contains("Antal\nl") && !text.matches("(?s).*Antal\\s+l\\b.*"), text);
        PdfTestSupport.assertNoHeavyHorizontalBars(PdfTestSupport.renderFirstPage(bytes));
        PdfTestSupport.saveArtifacts("table-hairline-rules", bytes);
    }

    @Test
    void resolvesMixedPointAndPercentColumns() throws Exception {
        var table = Table.withColumns(
            UnitValue.createPointValue(90),
            UnitValue.createPercentValue(100),
            UnitValue.createPointValue(70)
        ).useAllAvailableWidth();
        table.addHeaderRow("SKU", "Description", "Qty");
        table.addRow("A-01", "A long product name that should use leftover width", "12");
        var bytes = Pdf.create(document -> document.add(table));
        var text = PdfTestSupport.text(bytes);
        assertTrue(text.contains("A-01"));
        assertTrue(text.contains("leftover width"));
    }
}
