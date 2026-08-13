package org.skaldpdf;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExampleGalleryTest {
    @Test
    void writesOneHundredReviewDocuments() throws Exception {
        var buildTarget = Path.of("build", "example-gallery");
        assertEquals(100, ExampleGallery.writeAll(buildTarget));

        var downloads = Path.of(System.getProperty("user.home"), "Downloads");
        if (Files.isDirectory(downloads)) {
            assertEquals(100, ExampleGallery.writeAll(downloads.resolve("skald-examples")));
        }

        try (var files = Files.list(buildTarget)) {
            var pdfs = files.filter(path -> path.getFileName().toString().endsWith(".pdf")).toList();
            assertEquals(100, pdfs.size());
            for (var pdf : pdfs) {
                var header = Files.readAllBytes(pdf);
                assertTrue(header.length > 200, pdf.getFileName() + " is too small");
                assertEquals("%PDF-2.0", new String(header, 0, 8, java.nio.charset.StandardCharsets.US_ASCII));
            }
        }

        PdfTestSupport.saveArtifacts("gallery-invoice",
            Files.readAllBytes(buildTarget.resolve("01-invoice-modern.pdf")));
        PdfTestSupport.saveArtifacts("gallery-cover",
            Files.readAllBytes(buildTarget.resolve("96-gradient-cover.pdf")));
    }
}
