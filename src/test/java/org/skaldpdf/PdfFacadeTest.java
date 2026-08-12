package org.skaldpdf;

import org.skaldpdf.layout.Canvas;
import org.skaldpdf.layout.element.Paragraph;
import org.skaldpdf.layout.properties.TextAlignment;
import org.skaldpdf.layout.properties.VerticalAlignment;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PdfFacadeTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void supportsConciseCreationMergeAndRewrite() throws Exception {
        var first = Pdf.create(document -> document.add(new Paragraph("First page").bold()));
        var second = Pdf.create(document -> document.add(new Paragraph("Second page")));
        var merged = Pdf.merge(List.of(first, second));
        var rewritten = Pdf.rewrite(merged, document -> {
            var page = document.getPage(2);
            new Canvas(page, page.getCropBox()).showTextAligned(
                "Reviewed", 36, 24, TextAlignment.LEFT, VerticalAlignment.BOTTOM, 0
            );
        });

        try (var document = PdfTestSupport.load(rewritten)) {
            assertEquals(2, document.getNumberOfPages());
        }
        var text = PdfTestSupport.text(rewritten);
        assertTrue(text.contains("First page"));
        assertTrue(text.contains("Second page"));
        assertTrue(text.contains("Reviewed"));
    }

    @Test
    void supportsPathFirstCreationCompositionAndRewrite() throws Exception {
        var first = temporaryDirectory.resolve("first.pdf");
        var second = temporaryDirectory.resolve("second.pdf");
        var merged = temporaryDirectory.resolve("merged.pdf");
        var rewritten = temporaryDirectory.resolve("rewritten.pdf");

        Pdf.write(first, document -> document.add(new Paragraph("Direct path output").bold()));
        Pdf.write(second, document -> document.add(new Paragraph("Another path output")));
        Pdf.mergePaths(List.of(first, second), merged);
        Pdf.rewrite(merged, rewritten, document -> {
            var page = document.getPage(2);
            new Canvas(page, page.getCropBox()).showTextAligned(
                "Path workflow", 36, 24, TextAlignment.LEFT, VerticalAlignment.BOTTOM, 0
            );
        });

        var text = PdfTestSupport.text(java.nio.file.Files.readAllBytes(rewritten));
        assertTrue(text.contains("Direct path output"));
        assertTrue(text.contains("Another path output"));
        assertTrue(text.contains("Path workflow"));
    }
}
