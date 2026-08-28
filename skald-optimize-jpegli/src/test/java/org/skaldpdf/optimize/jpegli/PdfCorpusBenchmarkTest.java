package org.skaldpdf.optimize.jpegli;

import org.apache.pdfbox.Loader;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.skaldpdf.optimize.OptimizeOptions;
import org.skaldpdf.optimize.PdfOptimizer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** Opt-in benchmark for private corpora; PDF files are never copied into the repository. */
class PdfCorpusBenchmarkTest {
    @Test
    void benchmarksAndValidatesPrivateCorpusWhenConfigured() throws Exception {
        var configured = System.getenv("SKALD_PDF_CORPUS");
        Assumptions.assumeTrue(configured != null && !configured.isBlank(),
            "Set SKALD_PDF_CORPUS to run the private corpus benchmark");
        var corpus = Path.of(configured);
        Assumptions.assumeTrue(Files.isDirectory(corpus), "Configured corpus directory does not exist");
        var paths = new ArrayList<Path>();
        try (var files = Files.walk(corpus)) {
            files.filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".pdf"))
                .sorted().forEach(paths::add);
        }
        Assumptions.assumeFalse(paths.isEmpty(), "Configured corpus has no PDFs");

        var options = OptimizeOptions.attachments();
        var recompressor = new JpegliImageRecompressor();
        var csv = new StringBuilder("file,source_bytes,output_bytes,saved_bytes,millis,changed\n");
        long sourceBytes = 0;
        long outputBytes = 0;
        var changed = 0;
        var started = System.nanoTime();
        for (var path : paths) {
            var source = Files.readAllBytes(path);
            var itemStarted = System.nanoTime();
            var result = PdfOptimizer.recompress(source, options, recompressor);
            var millis = (System.nanoTime() - itemStarted) / 1_000_000;
            try (var before = Loader.loadPDF(source); var after = Loader.loadPDF(result)) {
                assertEquals(PdfBoxInvariant.capture(before), PdfBoxInvariant.capture(after), path.toString());
            }
            if (result != source) {
                changed++;
                assertArrayEquals(result, PdfOptimizer.recompress(result, options, recompressor), path.toString());
            }
            sourceBytes += source.length;
            outputBytes += result.length;
            csv.append(csv(path.getFileName().toString())).append(',').append(source.length).append(',')
                .append(result.length).append(',').append(source.length - result.length).append(',')
                .append(millis).append(',').append(result != source).append('\n');
        }
        var elapsedMillis = (System.nanoTime() - started) / 1_000_000;
        var report = """
            # Private PDF optimization corpus

            | Metric | Result |
            | --- | ---: |
            | PDFs | %,d |
            | Changed | %,d |
            | Source bytes | %,d |
            | Output bytes | %,d |
            | Saved bytes | %,d |
            | Saved | %.2f%% |
            | Elapsed | %,d ms |
            | Throughput | %.2f PDFs/s |
            """.formatted(paths.size(), changed, sourceBytes, outputBytes, sourceBytes - outputBytes,
            (sourceBytes - outputBytes) * 100.0 / sourceBytes, elapsedMillis,
            paths.size() * 1000.0 / elapsedMillis);
        var output = Path.of("skald-optimize-jpegli", "build", "benchmarks");
        Files.createDirectories(output);
        Files.writeString(output.resolve("private-pdf-corpus.md"), report);
        Files.writeString(output.resolve("private-pdf-corpus.csv"), csv);
        System.out.println(report);
    }

    private static String csv(String value) {
        return '"' + value.replace("\"", "\"\"") + '"';
    }
}
