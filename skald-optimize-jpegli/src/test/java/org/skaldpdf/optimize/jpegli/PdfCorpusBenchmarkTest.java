package org.skaldpdf.optimize.jpegli;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSStream;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.skaldpdf.optimize.OptimizeOptions;
import org.skaldpdf.optimize.PdfOptimizer;
import org.skaldpdf.pdf.EmbeddedImage;
import org.skaldpdf.pdf.PdfDocument;
import org.skaldpdf.pdf.PdfReader;

import java.nio.file.Files;
import java.nio.file.FileVisitOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** Opt-in benchmark for private corpora; PDF files are never copied into the repository. */
class PdfCorpusBenchmarkTest {
    @Test
    void benchmarksAndValidatesPrivateCorpusWhenConfigured() throws Exception {
        var configured = System.getenv("SKALD_PDF_CORPUS");
        var corpus = configured == null || configured.isBlank()
            ? Path.of("benchmark-corpus", "largest-250") : Path.of(configured);
        Assumptions.assumeTrue(Files.isDirectory(corpus),
            "Set SKALD_PDF_CORPUS or populate benchmark-corpus/largest-250");
        var paths = new ArrayList<Path>();
        try (var files = Files.walk(corpus, FileVisitOption.FOLLOW_LINKS)) {
            files.filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".pdf"))
                .sorted().forEach(paths::add);
        }
        Assumptions.assumeFalse(paths.isEmpty(), "Configured corpus has no PDFs");
        var sizes = new HashMap<Path, Long>();
        for (var path : paths) {
            sizes.put(path, Files.size(path));
        }
        paths.sort(Comparator.<Path>comparingLong(sizes::get).reversed()
            .thenComparing(path -> path.getFileName().toString()));

        var outputCorpusValue = System.getenv("SKALD_PDF_OUTPUT_CORPUS");
        var outputCorpus = outputCorpusValue == null || outputCorpusValue.isBlank()
            ? null : Path.of(outputCorpusValue);
        if (outputCorpus != null) {
            Files.createDirectories(outputCorpus);
        }

        var options = corpusOptions();
        var recompressor = new JpegliImageRecompressor();
        var csv = new StringBuilder("rank,file,source_bytes,output_bytes,saved_bytes,millis,changed,reason,detail,")
            .append("pages,streams,stream_bytes,image_streams,image_stream_bytes,images,safe_images,eligible_images\n");
        long sourceBytes = 0;
        long outputBytes = 0;
        var changed = 0;
        var timings = new ArrayList<Long>();
        var outcomes = new LinkedHashMap<String, long[]>();
        var payloads = new LinkedHashMap<String, long[]>();
        var started = System.nanoTime();
        var rank = 0;
        for (var path : paths) {
            rank++;
            var source = Files.readAllBytes(path);
            var payload = payloads.computeIfAbsent(
                java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(source)),
                ignored -> new long[] {0, source.length});
            payload[0]++;
            var itemStarted = System.nanoTime();
            var result = PdfOptimizer.recompress(source, options, recompressor);
            var millis = (System.nanoTime() - itemStarted) / 1_000_000;
            timings.add(millis);
            Structure structure;
            try (var before = Loader.loadPDF(source)) {
                structure = Structure.capture(before);
                if (result != source) {
                    try (var after = Loader.loadPDF(result)) {
                        assertEquals(PdfBoxInvariant.capture(before), PdfBoxInvariant.capture(after), path.toString());
                    }
                }
            }
            if (result != source) {
                changed++;
                assertArrayEquals(result, PdfOptimizer.recompress(result, options, recompressor), path.toString());
                if (outputCorpus != null) {
                    Files.write(outputCorpus.resolve("%03d-%s".formatted(rank, path.getFileName())), result);
                }
            }
            sourceBytes += source.length;
            outputBytes += result.length;
            var decision = Decision.explain(source, result != source, options);
            var outcome = outcomes.computeIfAbsent(decision.reason(), ignored -> new long[3]);
            outcome[0]++;
            outcome[1] += source.length;
            outcome[2] += source.length - result.length;
            csv.append(rank).append(',').append(csv(corpus.relativize(path).toString())).append(',')
                .append(source.length).append(',')
                .append(result.length).append(',').append(source.length - result.length).append(',')
                .append(millis).append(',').append(result != source).append(',')
                .append(csv(decision.reason())).append(',').append(csv(decision.detail())).append(',')
                .append(structure.pages()).append(',').append(structure.streams()).append(',')
                .append(structure.streamBytes()).append(',').append(structure.imageStreams()).append(',')
                .append(structure.imageStreamBytes()).append(',').append(decision.images()).append(',')
                .append(decision.safeImages()).append(',').append(decision.eligibleImages()).append('\n');
        }
        var elapsedMillis = (System.nanoTime() - started) / 1_000_000;
        timings.sort(Long::compareTo);
        var optimizerMillis = timings.stream().mapToLong(Long::longValue).sum();
        var duplicateFiles = payloads.values().stream().mapToLong(value -> value[0] - 1).sum();
        var duplicateBytes = payloads.values().stream()
            .mapToLong(value -> value[1] * (value[0] - 1)).sum();
        var report = new StringBuilder(String.format(Locale.ROOT, """
            # Private PDF optimization corpus

            | Metric | Result |
            | --- | ---: |
            | PDFs | %,d |
            | Max edge / JPEG quality / lossless quality | %,d / %.2f / %.2f |
            | Unique source payloads | %,d |
            | Exact duplicate files / bytes | %,d / %,d |
            | Changed | %,d |
            | Source bytes | %,d |
            | Output bytes | %,d |
            | Saved bytes | %,d |
            | Saved | %.2f%% |
            | Optimizer time | %,d ms |
            | Optimizer throughput | %.2f PDFs/s |
            | Optimizer p50 / p95 / p99 / max | %,d / %,d / %,d / %,d ms |
            | Validation wall time | %,d ms |

            ## Outcomes

            | Reason | PDFs | Source bytes | Saved bytes |
            | --- | ---: | ---: | ---: |
            """, paths.size(), options.maxEdge(), options.jpegQuality(), options.losslessQuality(),
            payloads.size(), duplicateFiles, duplicateBytes,
            changed, sourceBytes, outputBytes, sourceBytes - outputBytes,
            (sourceBytes - outputBytes) * 100.0 / sourceBytes, optimizerMillis,
            paths.size() * 1000.0 / Math.max(1, optimizerMillis),
            percentile(timings, 50), percentile(timings, 95), percentile(timings, 99),
            timings.getLast(), elapsedMillis));
        outcomes.entrySet().stream().sorted((left, right) -> Long.compare(right.getValue()[1], left.getValue()[1]))
            .forEach(entry -> report.append("| ").append(entry.getKey()).append(" | ")
                .append(entry.getValue()[0]).append(" | ").append(entry.getValue()[1]).append(" | ")
                .append(entry.getValue()[2]).append(" |\n"));
        var output = Path.of("skald-optimize-jpegli", "build", "benchmarks");
        Files.createDirectories(output);
        Files.writeString(output.resolve("private-pdf-corpus.md"), report);
        Files.writeString(output.resolve("private-pdf-corpus.csv"), csv);
        System.out.println(report);
    }

    static OptimizeOptions corpusOptions() {
        var defaults = OptimizeOptions.attachments();
        var builder = OptimizeOptions.builder();
        var edge = System.getenv("SKALD_PDF_MAX_EDGE");
        var jpegQuality = System.getenv("SKALD_PDF_JPEG_QUALITY");
        var losslessQuality = System.getenv("SKALD_PDF_LOSSLESS_QUALITY");
        var minimumLosslessBytes = System.getenv("SKALD_PDF_MINIMUM_LOSSLESS_BYTES");
        builder.maxEdge(edge == null || edge.isBlank() ? defaults.maxEdge() : Integer.parseInt(edge));
        builder.jpegQuality(jpegQuality == null || jpegQuality.isBlank()
            ? defaults.jpegQuality() : Float.parseFloat(jpegQuality));
        builder.losslessQuality(losslessQuality == null || losslessQuality.isBlank()
            ? defaults.losslessQuality() : Float.parseFloat(losslessQuality));
        builder.minimumLosslessBytes(minimumLosslessBytes == null || minimumLosslessBytes.isBlank()
            ? defaults.minimumLosslessBytes() : Integer.parseInt(minimumLosslessBytes));
        return builder.build();
    }

    private static long percentile(List<Long> sorted, int percentile) {
        var index = Math.max(0, (int) Math.ceil(sorted.size() * percentile / 100.0) - 1);
        return sorted.get(index);
    }

    private static String csv(String value) {
        return '"' + value.replace("\"", "\"\"") + '"';
    }

    private record Structure(int pages, int streams, long streamBytes,
                             int imageStreams, long imageStreamBytes) {
        static Structure capture(org.apache.pdfbox.pdmodel.PDDocument document) {
            var streams = 0;
            long streamBytes = 0;
            var imageStreams = 0;
            long imageStreamBytes = 0;
            for (var key : document.getDocument().getXrefTable().keySet()) {
                var object = document.getDocument().getObjectFromPool(key);
                if (object.getObject() instanceof COSStream stream) {
                    streams++;
                    var length = Math.max(0, stream.getLength());
                    streamBytes += length;
                    if (COSName.IMAGE.equals(stream.getCOSName(COSName.SUBTYPE))) {
                        imageStreams++;
                        imageStreamBytes += length;
                    }
                }
            }
            return new Structure(document.getNumberOfPages(), streams, streamBytes,
                imageStreams, imageStreamBytes);
        }
    }

    private record Decision(String reason, String detail, int images, int safeImages, int eligibleImages) {
        static Decision explain(byte[] source, boolean changed, OptimizeOptions options) {
            try (var document = new PdfDocument(new PdfReader(source))) {
                var constraints = document.canonicalRewriteConstraints();
                if (!constraints.isEmpty()) {
                    return new Decision("protected_document",
                        constraints.stream().map(Enum::name).sorted().toList().toString(),
                        0, 0, 0);
                }
                var images = document.importedImages();
                var safe = images.stream().filter(EmbeddedImage::safeToRecompress).toList();
                var eligible = safe.stream().filter(image -> eligible(image, options)).toList();
                if (changed) {
                    return new Decision("optimized_and_verified", imageDetails(images),
                        images.size(), safe.size(), eligible.size());
                }
                if (images.isEmpty()) {
                    return new Decision("no_qualifying_stream_savings",
                        "No page/Form image XObjects; canonical and lossless stream gains missed the document gates",
                        0, 0, 0);
                }
                if (safe.isEmpty()) {
                    return new Decision("unsupported_image_semantics", imageDetails(images),
                        images.size(), 0, 0);
                }
                if (eligible.isEmpty()) {
                    return new Decision("image_policy_exclusion", policyExclusions(safe, options),
                        images.size(), safe.size(), 0);
                }
                return new Decision("codec_or_savings_gate_rejected", imageDetails(eligible),
                    images.size(), safe.size(), eligible.size());
            } catch (RuntimeException unsupported) {
                var message = unsupported.getMessage();
                var detail = unsupported.getClass().getSimpleName()
                    + (message == null || message.isBlank() ? "" : ": " + message.replace('\n', ' '));
                return new Decision("unsupported_or_malformed_pdf", detail, 0, 0, 0);
            }
        }

        private static boolean eligible(EmbeddedImage image, OptimizeOptions options) {
            var pixels = (long) image.width() * image.height();
            return pixels > 0 && pixels <= options.maximumImagePixels()
                && (image.jpeg() && options.recompressJpeg()
                    || !image.jpeg() && options.convertLosslessRaster()
                    && image.encodedLength() >= options.minimumLosslessBytes());
        }

        private static String policyExclusions(List<EmbeddedImage> images, OptimizeOptions options) {
            var reasons = new LinkedHashSet<String>();
            for (var image : images) {
                var pixels = (long) image.width() * image.height();
                if (pixels <= 0 || pixels > options.maximumImagePixels()) {
                    reasons.add("pixel limit");
                } else if (image.jpeg() && !options.recompressJpeg()
                    || !image.jpeg() && !options.convertLosslessRaster()) {
                    reasons.add("codec disabled");
                } else if (!image.jpeg() && image.encodedLength() < options.minimumLosslessBytes()) {
                    reasons.add("lossless image below byte threshold");
                }
            }
            return reasons.toString();
        }

        private static String imageDetails(List<EmbeddedImage> images) {
            Set<String> filters = new LinkedHashSet<>();
            Set<String> colorSpaces = new LinkedHashSet<>();
            for (var image : images) {
                filters.add(image.filter());
                colorSpaces.add(image.colorSpace());
            }
            return "filters=" + filters + "; colorSpaces=" + colorSpaces;
        }
    }
}
