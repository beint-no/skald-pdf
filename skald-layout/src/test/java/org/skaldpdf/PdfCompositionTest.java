package org.skaldpdf;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.skaldpdf.colors.ColorConstants;
import org.skaldpdf.geom.PageSize;
import org.skaldpdf.layout.Canvas;
import org.skaldpdf.layout.Document;
import org.skaldpdf.layout.element.Paragraph;
import org.skaldpdf.layout.properties.TextAlignment;
import org.skaldpdf.layout.properties.VerticalAlignment;
import org.skaldpdf.pdf.PdfDocument;
import org.skaldpdf.pdf.PdfReader;
import org.skaldpdf.pdf.PdfWriter;
import org.skaldpdf.pdf.merge.PdfMerger;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

    @Test
    void importsAClassicPdf1xButAlwaysEmitsPdf20() throws Exception {
        var source = classicPdf14("Legacy supplier attachment");
        assertTrue(new String(source, 0, 8, java.nio.charset.StandardCharsets.US_ASCII).startsWith("%PDF-1."));
        var output = new ByteArrayOutputStream();
        try (var document = new PdfDocument(new PdfReader(source), new PdfWriter(output))) {
            var page = document.getPage(1);
            new Canvas(page, page.getCropBox()).setFontSize(10).showTextAligned(
                "Processed by a PDF 2.0 workflow", 36, 24,
                TextAlignment.LEFT, VerticalAlignment.BOTTOM, 0
            );
        }

        var bytes = output.toByteArray();
        assertTrue(new String(bytes, 0, 8, java.nio.charset.StandardCharsets.US_ASCII).startsWith("%PDF-2.0"));
        var text = PdfTestSupport.text(bytes);
        assertTrue(text.contains("Legacy supplier attachment"));
        assertTrue(text.contains("Processed by a PDF 2.0 workflow"));
    }

    @Test
    void stripsLaunchAndJavascriptWhenImporting() throws Exception {
        var source = dangerousPdf();
        var asciiSource = new String(source, java.nio.charset.StandardCharsets.ISO_8859_1);
        assertTrue(asciiSource.contains("/JavaScript") || asciiSource.contains("/Launch"));
        var rewritten = Pdf.rewrite(source, document -> {
            var page = document.getPage(1);
            new Canvas(page, page.getCropBox()).setFontSize(9).showTextAligned(
                "Reviewed", 36, 24, TextAlignment.LEFT, VerticalAlignment.BOTTOM, 0
            );
        });
        var ascii = new String(rewritten, java.nio.charset.StandardCharsets.ISO_8859_1);
        assertTrue(ascii.startsWith("%PDF-2.0"));
        assertFalse(ascii.contains("/JavaScript"));
        assertFalse(ascii.contains("/Launch"));
        assertFalse(ascii.contains("app.alert"));
        assertFalse(ascii.contains("evil.exe"));
        assertTrue(PdfTestSupport.text(rewritten).contains("Reviewed"));
    }

    @Test
    void rejectsEncryptedInputBeforeComposition() throws Exception {
        var source = classicPdf14("Confidential");
        var encrypted = new ByteArrayOutputStream();
        try (var document = org.apache.pdfbox.Loader.loadPDF(source)) {
            var policy = new StandardProtectionPolicy("owner", "user", new AccessPermission());
            policy.setEncryptionKeyLength(128);
            document.protect(policy);
            document.save(encrypted);
        }
        assertThrows(IllegalArgumentException.class, () -> new PdfDocument(new PdfReader(encrypted.toByteArray())));
    }

    private static byte[] onePage(String title) {
        var output = new ByteArrayOutputStream();
        var document = new Document(new PdfDocument(new PdfWriter(output)), PageSize.A4);
        document.add(new Paragraph(title).bold().setFontSize(20));
        document.close();
        return output.toByteArray();
    }

    private static byte[] dangerousPdf() throws Exception {
        var content = "BT 72 720 Td (Supplier attachment) Tj ET\n";
        var objects = java.util.List.of(
            "1 0 obj << /Type /Catalog /Pages 2 0 R >> endobj\n",
            "2 0 obj << /Type /Pages /Count 1 /Kids [3 0 R] >> endobj\n",
            """
            3 0 obj << /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Contents 4 0 R \
            /AA << /O << /S /JavaScript /JS (app.alert('x')) >> >> /Annots [5 0 R] >> endobj
            """,
            "4 0 obj << /Length " + content.length() + " >> stream\n" + content + "endstream endobj\n",
            "5 0 obj << /Type /Annot /Subtype /Link /Rect [72 700 192 720] /Border [0 0 0] "
                + "/A << /S /Launch /F (evil.exe) >> >> endobj\n"
        );
        var output = new ByteArrayOutputStream();
        output.write("%PDF-1.4\n".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        var offsets = new int[objects.size() + 1];
        for (int index = 0; index < objects.size(); index++) {
            offsets[index + 1] = output.size();
            output.write(objects.get(index).getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        }
        var xrefStart = output.size();
        var xref = new StringBuilder("xref\n0 ").append(objects.size() + 1).append('\n');
        xref.append("0000000000 65535 f \n");
        for (int index = 1; index <= objects.size(); index++) {
            xref.append("%010d 00000 n \n".formatted(offsets[index]));
        }
        xref.append("trailer << /Size ").append(objects.size() + 1)
            .append(" /Root 1 0 R >>\nstartxref\n").append(xrefStart).append("\n%%EOF\n");
        output.write(xref.toString().getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        return output.toByteArray();
    }

    private static byte[] classicPdf14(String text) throws Exception {
        var output = new ByteArrayOutputStream();
        try (var document = new PDDocument()) {
            document.setVersion(1.4f);
            var page = new PDPage();
            document.addPage(page);
            try (var content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 14);
                content.newLineAtOffset(72, 720);
                content.showText(text);
                content.endText();
            }
            document.save(output);
        }
        return output.toByteArray();
    }
}
