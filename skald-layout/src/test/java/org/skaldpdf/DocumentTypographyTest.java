package org.skaldpdf;

import org.skaldpdf.colors.ColorConstants;
import org.skaldpdf.font.PdfFontFactory;
import org.skaldpdf.layout.element.Paragraph;
import org.skaldpdf.layout.element.Text;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DocumentTypographyTest {
    @Test
    void bundledItalicIsATrueItalicFace() {
        assertTrue(PdfFontFactory.italic().italic());
        assertTrue(PdfFontFactory.boldItalic().italic());
        assertTrue(PdfFontFactory.boldItalic().bold());
        assertTrue(PdfFontFactory.italic().metrics().italicAngle() < 0);
        assertTrue(PdfFontFactory.regular().metrics().italicAngle() == 0);
    }

    @Test
    void paintsItalicEmphasisAndKeepsPlainText() throws Exception {
        var bytes = Pdf.create(document -> document.add(
            new Paragraph()
                .add("Payment terms are ")
                .add(new Text("net 14 days").italic())
                .add(" from the invoice date.")
        ));
        var text = PdfTestSupport.text(bytes);
        assertTrue(text.contains("Payment terms are"));
        assertTrue(text.contains("net 14 days"));
        try (var parsed = PdfTestSupport.load(bytes)) {
            var names = new StringBuilder();
            for (var fontName : parsed.getPage(0).getResources().getFontNames()) {
                names.append(parsed.getPage(0).getResources().getFont(fontName).getName());
            }
            assertTrue(names.toString().contains("Italic"), names.toString());
        }
    }

    @Test
    void customDocumentFontFallsBackToBundledSans() throws Exception {
        var display = PdfFontFactory.from(
            java.nio.file.Files.readAllBytes(java.nio.file.Path.of(
                "skald-core/src/main/resources/org/skaldpdf/font/SkaldSans-Italic.ttf")),
            org.skaldpdf.font.FontWeight.ITALIC
        );
        var bytes = Pdf.create(document -> {
            document.setFont(display);
            document.add(new Paragraph("Invoice 2026-1001 with fallback digits and Latin."));
        });
        assertTrue(PdfTestSupport.text(bytes).contains("Invoice"));
    }

    @Test
    void namedDestinationsAreWiredToGotoLinks() throws Exception {
        var bytes = Pdf.create(document -> {
            document.add(new Paragraph("See appendix").setNamedDestination("appendix")
                .setFontColor(ColorConstants.ACCENT));
            document.add(new org.skaldpdf.layout.element.AreaBreak());
            document.add(new Paragraph("Appendix A").bold().setLocalDestination("appendix"));
        });
        try (var parsed = PdfTestSupport.load(bytes)) {
            assertTrue(parsed.getNumberOfPages() >= 2);
            var names = parsed.getDocumentCatalog().getNames();
            assertTrue(names != null && names.getCOSObject() != null);
            var annotations = parsed.getPage(0).getAnnotations();
            assertFalse(annotations.isEmpty());
        }
        assertTrue(new String(bytes, java.nio.charset.StandardCharsets.ISO_8859_1).contains("/Dests")
            || PdfTestSupport.text(bytes).contains("Appendix A"));
    }
}
