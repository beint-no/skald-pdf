package no.beint.skald;

import no.beint.skald.colors.ColorConstants;
import no.beint.skald.geom.PageSize;
import no.beint.skald.layout.Canvas;
import no.beint.skald.layout.Document;
import no.beint.skald.layout.element.Paragraph;
import no.beint.skald.layout.properties.TextAlignment;
import no.beint.skald.layout.properties.VerticalAlignment;
import no.beint.skald.pdf.PdfDocument;
import no.beint.skald.pdf.PdfReader;
import no.beint.skald.pdf.PdfWriter;
import no.beint.skald.pdf.merge.PdfMerger;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PdfCompositionTest {
    @Test
    void mergesAndStampsDocuments() throws Exception {
        var first = onePage("Invoice 1001");
        var second = onePage("Reminder 1001");
        var mergedOutput = new ByteArrayOutputStream();
        var merged = new PdfDocument(new PdfWriter(mergedOutput));
        var merger = new PdfMerger(merged);
        for (var bytes : new byte[][] { first, second }) {
            try (var source = new PdfDocument(new PdfReader(new ByteArrayInputStream(bytes)))) {
                merger.merge(source, 1, source.getNumberOfPages());
            }
        }
        merged.close();

        var stampedOutput = new ByteArrayOutputStream();
        try (var document = new PdfDocument(
            new PdfReader(new ByteArrayInputStream(mergedOutput.toByteArray())), new PdfWriter(stampedOutput))) {
            for (int page = 1; page <= document.getNumberOfPages(); page++) {
                var pdfPage = document.getPage(page);
                var canvas = new Canvas(pdfPage, pdfPage.getPageSize());
                canvas.setFontSize(9).setFontColor(ColorConstants.GRAY).showTextAligned(
                    "Signed electronically", pdfPage.getPageSize().getWidth() / 2, 18,
                    TextAlignment.CENTER, VerticalAlignment.BOTTOM, 0
                );
            }
        }

        try (var parsed = PdfTestSupport.load(stampedOutput.toByteArray())) {
            assertEquals(2, parsed.getNumberOfPages());
        }
        var text = PdfTestSupport.text(stampedOutput.toByteArray());
        assertTrue(text.contains("Invoice 1001"));
        assertTrue(text.contains("Reminder 1001"));
        assertTrue(text.contains("Signed electronically"));
        PdfTestSupport.saveArtifacts("merged-reminder", stampedOutput.toByteArray());
    }

    private static byte[] onePage(String title) {
        var output = new ByteArrayOutputStream();
        var document = new Document(new PdfDocument(new PdfWriter(output)), PageSize.A4);
        document.add(new Paragraph(title).simulateBold().setFontSize(20));
        document.close();
        return output.toByteArray();
    }
}
