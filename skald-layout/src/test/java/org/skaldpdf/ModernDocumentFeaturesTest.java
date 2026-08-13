package org.skaldpdf;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import org.skaldpdf.barcode.Code128Barcode;
import org.skaldpdf.colors.ColorConstants;
import org.skaldpdf.colors.DeviceRgb;
import org.skaldpdf.colors.LinearGradient;
import org.skaldpdf.geom.PageSize;
import org.skaldpdf.layout.borders.DashedBorder;
import org.skaldpdf.layout.element.Div;
import org.skaldpdf.layout.element.Image;
import org.skaldpdf.layout.element.ListBlock;
import org.skaldpdf.layout.element.Paragraph;
import org.skaldpdf.layout.element.Table;
import org.skaldpdf.layout.properties.TextAlignment;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModernDocumentFeaturesTest {
    @Test
    void paintsRunningChromeAndModernCatalogFeatures() throws Exception {
        var bytes = Pdf.create(document -> {
            document.setTitle("Quarterly review")
                .setAuthor("Skald PDF")
                .setSubject("Modern document chrome")
                .setKeywords("report, pdf-2.0")
                .setLanguage("en-GB")
                .setMargins(48, 48, 40, 48)
                .setHeader(22, page -> new Paragraph("Northstar Ledger · Confidential")
                    .setFontSize(9).setFontColor(ColorConstants.MUTED))
                .setFooter(18, page -> new Paragraph("Page " + page.pageNumber() + " / " + page.pageCount())
                    .setFontSize(9).setTextAlignment(TextAlignment.CENTER).setFontColor(ColorConstants.MUTED));
            document.addOutline("Overview", 1);
            document.addOutline("Notes", 2);
            document.add(new Paragraph("Quarterly review").bold().setFontSize(22).setFontColor(ColorConstants.ACCENT));
            document.add(new Paragraph(
                "Skald writes PDF 2.0 only. Running headers, footers, and outlines belong in generated "
                    + "reports rather than being reconstructed by a second layout engine."
            ).justify().setFontSize(11).setMarginTop(10));
            document.add(new ListBlock(ListBlock.Marker.DECIMAL)
                .add("Page chrome is reserved before flow starts.")
                .add("Outlines point at finished pages.")
                .add("URI links stay as PDF 2.0 annotations."));
            document.add(new Paragraph("Notes").bold().setFontSize(16).setDestinationUri("https://github.com/beint-no/skald-pdf"));
            document.add(new Paragraph("Second page keeps the same running header and footer.")
                .setKeepWithNext(false));
            for (int index = 0; index < 40; index++) {
                document.add(new Paragraph("Continuing note " + (index + 1) + " with enough copy to cross a page boundary.")
                    .setFontSize(11));
            }
        });

        var ascii = new String(bytes, java.nio.charset.StandardCharsets.ISO_8859_1);
        assertTrue(ascii.startsWith("%PDF-2.0"));
        assertTrue(ascii.contains("/DisplayDocTitle true"));
        assertTrue(ascii.contains("/Lang (en-GB)"));
        try (var parsed = PdfTestSupport.load(bytes)) {
            assertTrue(parsed.getNumberOfPages() >= 2);
            assertEquals("en-GB", parsed.getDocumentCatalog().getLanguage());
            assertTrue(parsed.getDocumentCatalog().getDocumentOutline() != null);
            assertTrue(parsed.getPage(0).getAnnotations().stream()
                .anyMatch(annotation -> "Link".equals(annotation.getSubtype())));
        }
        var text = PdfTestSupport.text(bytes);
        assertTrue(text.contains("Page 1 /"));
        assertTrue(text.contains("Page 2 /"));
        assertTrue(text.contains("Northstar Ledger"));
        PdfTestSupport.saveArtifacts("modern-chrome", bytes);
        PdfTestSupport.assertVisibleInk(PdfTestSupport.renderFirstPage(bytes));
    }

    @Test
    void drawsGradientsDashedRulesAndRoundedSurfaces() throws Exception {
        var bytes = Pdf.create(document -> document.add(
            new Div()
                .setBackground(LinearGradient.vertical(DeviceRgb.hex("#18533F"), DeviceRgb.hex("#0F2F26")))
                .setBorderRadius(12)
                .setPadding(18)
                .setBorder(new DashedBorder(ColorConstants.ACCENT, 1.1f))
                .add(new Paragraph("Rounded gradient panel").bold().setFontSize(18).setFontColor(ColorConstants.WHITE))
                .add(new Paragraph("Dashed strokes and axial shadings are native PDF 2.0 operators.")
                    .setFontColor(ColorConstants.WHITE).setFontSize(11).setMarginTop(6))
        ));
        try (var parsed = PdfTestSupport.load(bytes)) {
            var shadings = parsed.getPage(0).getResources().getShadingNames();
            assertTrue(shadings != null && shadings.iterator().hasNext(), "page should register an axial shading");
        }
        PdfTestSupport.saveArtifacts("modern-surface", bytes);
        PdfTestSupport.assertVisibleInk(PdfTestSupport.renderFirstPage(bytes));
    }

    @Test
    void rejectsIncompleteTableRows() {
        var table = new Table(3);
        assertThrows(IllegalArgumentException.class, () -> table.addRow("only", "two"));
    }

    @Test
    void renderedCode128IsMachineReadable() throws Exception {
        var barcode = new Code128Barcode("SKALD-2026-OK")
            .withModuleWidth(1.2f)
            .withBarHeight(42f)
            .withFontSize(9f);
        var bytes = Pdf.create(new PageSize(360, 140), document -> {
            document.setMargins(16, 16, 16, 16);
            document.add(new Image(barcode).scaleToFit(320, 100));
        });
        var rendered = PdfTestSupport.renderFirstPage(bytes);
        var pixels = rendered.getRGB(0, 0, rendered.getWidth(), rendered.getHeight(), null, 0, rendered.getWidth());
        var source = new RGBLuminanceSource(rendered.getWidth(), rendered.getHeight(), pixels);
        var result = new MultiFormatReader().decode(new BinaryBitmap(new HybridBinarizer(source)));
        assertEquals("SKALD-2026-OK", result.getText());
        PdfTestSupport.saveArtifacts("code128-label", bytes);
    }

    @Test
    void parsesCompactColorAndPageHelpers() {
        assertEquals(24, DeviceRgb.hex("#18533F").redValue());
        assertEquals(83, DeviceRgb.hex("18533F").greenValue());
        assertEquals(PageSize.A4.getHeight(), PageSize.A4.landscape().getWidth(), 0.01f);
        assertTrue(PageSize.A5.getWidth() < PageSize.A4.getWidth());
    }
}
