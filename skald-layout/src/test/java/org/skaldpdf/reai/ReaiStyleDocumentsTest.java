package org.skaldpdf.reai;

import org.skaldpdf.PdfTestSupport;
import org.skaldpdf.pdf.SignatureField;
import org.skaldpdf.sign.PdfSigner;
import org.skaldpdf.sign.SigningKey;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.DynamicTest;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReaiStyleDocumentsTest {
    @TestFactory
    Stream<DynamicTest> rendersEveryReaiReplica() throws Exception {
        var logo = PdfTestSupport.sampleLogo();
        var documents = ReaiStyleDocuments.all(logo);
        assertEquals(15, documents.size());
        return documents.entrySet().stream().map(entry -> DynamicTest.dynamicTest(entry.getKey(), () -> {
            var bytes = entry.getValue();
            assertTrue(new String(bytes, 0, 8, StandardCharsets.US_ASCII).startsWith("%PDF-2.0"));
            var text = PdfTestSupport.text(bytes);
            assertTrue(text.contains("Nordlys Handel AS"), entry.getKey() + " missing company");
            assertTrue(text.contains("Fjordbutikken AS"), entry.getKey() + " missing customer");
            assertTrue(text.contains("NO999888777MVA") || text.contains("999888777"),
                entry.getKey() + " missing organisation number");
            PdfTestSupport.assertVisibleInk(PdfTestSupport.renderFirstPage(bytes));
            PdfTestSupport.saveArtifacts("reai-" + entry.getKey(), bytes);
        }));
    }

    @Test
    void writesComparableSamplesToOneFolder() throws Exception {
        var logo = PdfTestSupport.sampleLogo();
        var documents = ReaiStyleDocuments.all(logo);
        writeAll(Path.of("build", "reai-compare"), documents);
        var downloads = Path.of(System.getProperty("user.home"), "Downloads");
        if (Files.isDirectory(downloads)) {
            var target = downloads.resolve("skald-reai-compare");
            writeAll(target, documents);
            Files.writeString(target.resolve("README.txt"), comparisonReadme());
        }
        Files.writeString(Path.of("build", "reai-compare", "README.txt"), comparisonReadme());
    }

    @Test
    void norwegianInvoiceMatchesReaiLabelsAndStructure() throws Exception {
        var bytes = ReaiStyleDocuments.invoice(ReaiStyleDocuments.sampleInvoice(), PdfTestSupport.sampleLogo());
        var text = PdfTestSupport.text(bytes);
        var compact = text.replaceAll("\\s+", "");
        for (var expected : new String[] {
            "Faktura", "Fakturanr.", "1001", "Fakturadato:", "12.08.2026",
            "Organisasjonsnummer:", "NO999888777MVA",
            "Betalingsinformasjon", "Forfallsdato:", "26.08.2026",
            "Kontonummer", "1503 4567 890", "IBAN", "NO93 1503 4567 890",
            "Beskrivelse", "Antall", "MVA", "Til betaling",
            "Vennligst oppgi fakturanummer", "ved betaling",
            "Denne fakturaen er sendt med ReAI"
        }) {
            assertTrue(text.contains(expected) || compact.contains(expected.replaceAll("\\s+", "")),
                "Missing ReAI label: " + expected + "\n" + text);
        }
        try (var parsed = PdfTestSupport.load(bytes)) {
            assertEquals(1, parsed.getNumberOfPages());
        }
    }

    @Test
    void creditNoteAndPaidCopyUseReaiTitles() throws Exception {
        var credit = PdfTestSupport.text(ReaiStyleDocuments.invoice(
            ReaiStyleDocuments.creditNote(), PdfTestSupport.sampleLogo()));
        assertTrue(credit.contains("Kreditnota"));
        assertTrue(credit.contains("Kreditnota for faktura 1001 datert 12.08.2026"));
        assertTrue(credit.contains("Beløp"));

        var paid = PdfTestSupport.text(ReaiStyleDocuments.invoice(
            ReaiStyleDocuments.paidCopy(), PdfTestSupport.sampleLogo()));
        assertTrue(paid.contains("Betalt fakturakopi"));
        assertTrue(paid.contains("Betalingskvittering"));
        assertTrue(paid.contains("Betalt beløp"));
        assertTrue(paid.contains("Utestående beløp"));
    }

    @Test
    void longInvoicePaginatesAndReminderHasCollectionCopy() throws Exception {
        var longInvoice = ReaiStyleDocuments.invoice(
            ReaiStyleDocuments.longInvoice(), PdfTestSupport.sampleLogo());
        try (var parsed = PdfTestSupport.load(longInvoice)) {
            assertTrue(parsed.getNumberOfPages() >= 2, "28-line invoice should paginate");
        }
        var reminder = PdfTestSupport.text(ReaiStyleDocuments.reminder(false, PdfTestSupport.sampleLogo()));
        assertTrue(reminder.contains("Purring"));
        assertTrue(reminder.contains("Purregebyr"));
        var collection = PdfTestSupport.text(ReaiStyleDocuments.reminder(true, PdfTestSupport.sampleLogo()));
        assertTrue(collection.contains("Betalingsoppfordring"));
        assertTrue(collection.contains("tvangsfullbyrdelsesloven"));
    }

    @Test
    void signedInvoiceKeepsReaiLayoutAndVerifies() throws Exception {
        var unsigned = ReaiStyleDocuments.invoice(
            ReaiStyleDocuments.sampleInvoice(), PdfTestSupport.sampleLogo());
        var key = SigningKey.selfSigned("Nordlys Handel AS");
        var signed = PdfSigner.sign(unsigned, key, SignatureField.invisible("InvoiceSeal")
            .withReason("Issued invoice 1001")
            .withLocation("Oslo, Norway")
            .withPdfDate("D:20260813120000Z"));
        var text = PdfTestSupport.text(signed);
        assertTrue(text.contains("Faktura"));
        assertTrue(text.contains("Til betaling"));
        var verification = PdfSigner.verifySingle(signed);
        assertTrue(verification.valid(), String.join("; ", verification.notes()));
        PdfTestSupport.saveArtifacts("reai-16-faktura-signert", signed);
        var compare = Path.of("build", "reai-compare");
        Files.createDirectories(compare);
        Files.write(compare.resolve("16-faktura-signert.pdf"), signed);
        var downloads = Path.of(System.getProperty("user.home"), "Downloads", "skald-reai-compare");
        if (Files.isDirectory(downloads.getParent())) {
            Files.createDirectories(downloads);
            Files.write(downloads.resolve("16-faktura-signert.pdf"), signed);
        }
    }

    private static void writeAll(Path directory, Map<String, byte[]> documents) throws Exception {
        Files.createDirectories(directory);
        for (var entry : documents.entrySet()) {
            Files.write(directory.resolve(entry.getKey() + ".pdf"), entry.getValue());
        }
    }

    private static String comparisonReadme() {
        return """
            Skald replicas of ReAI invoice PDFs
            ==================================

            These files follow ReAI's InvoicePdfGenerator / OrderPdfGenerator /
            InvoiceReminderPdfGenerator layout (source of truth in ~/r/reai):

              A4, 40 pt margins
              company name 14 pt bold, right
              2 pt black rule
              address and org-number table right-aligned
              customer block left
              Faktura / Kreditnota / Betalt fakturakopi 18 pt
              payment table right
              7- or 8-column line table, 9 pt
              2 pt summary rules
              grey ReAI branding line

            What will not match a live ReAI DB PDF
            --------------------------------------
            ReAI currently writes PDF 1.x with unembedded Helvetica via iText.
            Skald writes PDF 2.0 and embeds a compact Skald Sans / IBM Plex subset.
            Metrics therefore differ by a millimetre or two, but labels, columns,
            rules, and reading order are the same.

            Live database blobs were not copied (they contain customer data).
            Compare these generated files against a ReAI invoice you already have.

            16-faktura-signert.pdf is the same invoice sealed with skald-sign
            (PAdES-B-B attributes, Adobe.PPKLite / adbe.pkcs7.detached). That is
            an integrity seal, not a qualified eIDAS signature.
            """;
    }
}
