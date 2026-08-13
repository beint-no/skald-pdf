package org.skaldpdf;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

class TypicalBusinessDocumentsTest {
    @TestFactory
    Stream<DynamicTest> rendersEveryTypicalBusinessDocument() throws Exception {
        var logo = PdfTestSupport.sampleLogo();
        var documents = TypicalBusinessDocuments.all(logo);
        assertTrue(documents.size() >= 40, "typical corpus should stay large, was " + documents.size());
        var output = Path.of("build", "typical-documents");
        Files.createDirectories(output);
        var downloads = Path.of(System.getProperty("user.home"), "Downloads", "skald-typical");
        var writeDownloads = Files.isDirectory(downloads.getParent());
        if (writeDownloads) {
            Files.createDirectories(downloads);
        }
        return documents.values().stream().map(document -> DynamicTest.dynamicTest(document.name(), () -> {
            var bytes = document.generator().get();
            assertTrue(bytes.length > 200, document.name() + " is too small");
            assertTrue(new String(bytes, 0, 8, StandardCharsets.US_ASCII).startsWith("%PDF-2.0"));
            var text = PdfTestSupport.text(bytes);
            for (var expected : document.expectedText()) {
                var normalizedText = text.replaceAll("\\s+", "");
                var normalizedExpected = expected.replaceAll("\\s+", "");
                assertTrue(text.contains(expected) || normalizedText.contains(normalizedExpected),
                    document.name() + " is missing: " + expected + "\n" + text);
            }
            PdfTestSupport.assertVisibleInk(PdfTestSupport.renderFirstPage(bytes));
            Files.write(output.resolve(document.name() + ".pdf"), bytes);
            if (writeDownloads) {
                Files.write(downloads.resolve(document.name() + ".pdf"), bytes);
            }
            PdfTestSupport.saveArtifacts("typical-" + document.name(), bytes);
        }));
    }

    @Test
    void invoiceFamilyCoversTheAccountingPath() throws Exception {
        var logo = PdfTestSupport.sampleLogo();
        var names = TypicalBusinessDocuments.all(logo).keySet();
        for (var required : new String[] {
            "invoice-no", "credit-note", "paid-copy", "reminder", "collection-notice",
            "packing-slip", "order-confirmation", "ean13-sticker", "code128-carton", "invoice-qr"
        }) {
            assertTrue(names.contains(required), "missing typical document " + required);
        }
    }
}
