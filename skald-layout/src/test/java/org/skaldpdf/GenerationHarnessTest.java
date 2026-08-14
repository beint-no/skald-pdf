package org.skaldpdf;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GenerationHarnessTest {
    private static final double SIZE_TOLERANCE = 0.35;
    private static final Path BASELINE = Path.of("skald-layout", "src", "test", "resources",
        "benchmarks", "baseline.json");

    @Test
    void measuresCorpusSizeAndSpeedAndWritesAReport() throws Exception {
        var report = GenerationHarness.run(2);
        GenerationHarness.write(Path.of("build", "benchmarks"), report);

        assertTrue(report.samples().size() >= 40, "Harness should cover the typical + ReAI corpus");
        assertTrue(report.totalNanos() < 30_000_000_000L,
            "Two timed passes of the corpus should stay under 30s, was " + report.totalNanos());

        var invoice = report.sample("reai-invoice");
        assertTrue(invoice.bytes() > 8_000, "Embedded-font invoice should be larger than Helvetica 2 KiB");
        assertTrue(invoice.bytes() < 80_000, "Ordinary invoice should stay compact, was " + invoice.bytes());
        assertTrue(invoice.pages() >= 1);

        var longInvoice = report.sample("reai-long-invoice");
        assertTrue(longInvoice.pages() >= 2);
        assertTrue(longInvoice.bytes() < 120_000, "28-line invoice should stay well under 120 KiB");

        if (Files.isRegularFile(BASELINE)) {
            var baseline = GenerationHarness.readBaseline(BASELINE);
            assertFalse(baseline.isEmpty(), "Baseline must list sample sizes");
            for (var sample : report.samples()) {
                var expected = baseline.get(sample.name());
                if (expected == null) {
                    continue;
                }
                var ratio = sample.bytes() / (double) expected;
                assertTrue(ratio < 1.0 + SIZE_TOLERANCE,
                    sample.name() + " grew from " + expected + " to " + sample.bytes() + " bytes");
                assertTrue(ratio > 0.4,
                    sample.name() + " shrank unexpectedly from " + expected + " to " + sample.bytes()
                        + " bytes — update the baseline if this is intentional");
            }
        }
    }

    @Test
    void productionHelveticaInvoiceIsTheSizeFloorNotTheTarget() throws Exception {
        var prod = Path.of(System.getProperty("user.home"), "Downloads", "invoice-RF41202600033.pdf");
        if (!Files.isRegularFile(prod)) {
            return;
        }
        assertTrue(Files.size(prod) < 4_096);
        var replica = org.skaldpdf.reai.ReaiStyleDocuments.invoice(
            org.skaldpdf.reai.ReaiStyleDocuments.respiroPaidCopy(), PdfTestSupport.sampleLogo());
        assertTrue(replica.length > Files.size(prod) * 4,
            "PDF 2.0 embedded subset must be larger than unembedded Helvetica");
        assertTrue(replica.length < 80_000);
    }
}
