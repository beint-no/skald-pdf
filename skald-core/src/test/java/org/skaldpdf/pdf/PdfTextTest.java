package org.skaldpdf.pdf;

import org.skaldpdf.font.PdfFont;
import org.skaldpdf.fonts.SkaldSans;
import org.skaldpdf.geom.PageSize;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PdfTextTest {
    @Test
    void extractsToUnicodeTextFromAGeneratedPage() {
        var output = new ByteArrayOutputStream();
        try (var pdf = new PdfDocument(new PdfWriter(output))) {
            var page = pdf.addNewPage(PageSize.A5);
            var font = SkaldSans.regular();
            show(page, font, "Invoice 2026-1001", 16, 36, 400);
            show(page, font, "Net 14 days", 11, 36, 380);
        }
        var text = PdfText.extract(output.toByteArray());
        assertTrue(text.contains("Invoice 2026-1001"), text);
        assertTrue(text.contains("Net 14 days"), text);
    }

    private static void show(PdfPage page, PdfFont font, String value, float size, float x, float y) {
        var run = font.glyphRun(value);
        var fontName = page.registerFont(font, run);
        var operators = new StringBuilder("0 0 0 rg\nBT\n/").append(fontName).append(' ')
            .append(PdfNumbers.format(size)).append(" Tf\n1 0 0 1 ")
            .append(PdfNumbers.format(x)).append(' ').append(PdfNumbers.format(y)).append(" Tm\n<");
        for (var glyph : run.glyphs()) {
            PdfNumbers.appendHex4(operators, glyph);
        }
        page.append(operators.append("> Tj\nET\n").toString());
    }
}
