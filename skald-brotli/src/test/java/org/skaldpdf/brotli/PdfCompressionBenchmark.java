package org.skaldpdf.brotli;

import org.skaldpdf.optimize.OptimizeOptions;
import org.skaldpdf.optimize.PdfOptimizer;
import org.skaldpdf.pdf.Compression;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Repeatable, lossless stream-compression benchmark; file I/O is outside timing. */
public final class PdfCompressionBenchmark {
    private PdfCompressionBenchmark() {}

    public static void main(String[] args) throws Exception {
        if (!Compression.BROTLI_BALANCED.available())
            throw new IllegalStateException("Brotli encoder is required");
        var directory = Path.of(args.length == 0 ? "build/use-case-pdfs" : args[0]);
        var inputs = new ArrayList<byte[]>();
        try (var paths =
                Files.isDirectory(directory)
                        ? Files.list(directory)
                        : java.util.stream.Stream.of(directory)) {
            for (var path : paths.filter(p -> p.toString().endsWith(".pdf")).sorted().toList())
                inputs.add(Files.readAllBytes(path));
        }
        if (inputs.isEmpty()) throw new IllegalArgumentException("No PDF inputs");
        var policies =
                List.of(
                        Compression.MAXIMUM,
                        Compression.BROTLI_FAST,
                        Compression.BROTLI_BALANCED,
                        Compression.BROTLI_MAXIMUM);
        var options =
                policies.stream()
                        .map(policy ->
                                OptimizeOptions.builder()
                                        .streamCompression(policy)
                                        .recompressJpeg(false)
                                        .convertLosslessRaster(false)
                                        .removeTrueTypeHinting(false)
                                        .removeUnusedGlyphs(false)
                                        .deduplicateImagesLosslessly(false)
                                        .deduplicateFontProgramsLosslessly(false)
                                        .preserveConformanceProfiles(true)
                                        .minimumSavingsBytes(1)
                                        .minimumSavingsPercent(0)
                                        .build())
                        .toList();
        for (var option : options) for (var input : inputs) PdfOptimizer.recompress(input, option);
        var times = new long[4][inputs.size()][3];
        var sizes = new int[4][inputs.size()];
        var changed = new int[4];
        for (int repeat = 0; repeat < 3; repeat++) {
            for (int document = 0; document < inputs.size(); document++) {
                var input = inputs.get(document);
                for (int order = 0; order < 4; order++) {
                    var policy = (order + repeat + document) % 4;
                    var start = System.nanoTime();
                    var result = PdfOptimizer.recompress(input, options.get(policy));
                    times[policy][document][repeat] = System.nanoTime() - start;
                    if (repeat == 0 && result != input) changed[policy]++;
                    if (repeat != 0 && result.length != sizes[policy][document])
                        throw new AssertionError("Non-deterministic size");
                    sizes[policy][document] = result.length;
                }
            }
        }
        var sourceBytes = inputs.stream().mapToLong(a -> a.length).sum();
        var csv =
                new StringBuilder(
                        "policy,documents,changed,input_bytes,output_bytes,saved_percent,sum_median_ms\n");
        for (int policy = 0; policy < 4; policy++) {
            long bytes = 0, nanos = 0;
            for (int document = 0; document < inputs.size(); document++) {
                bytes += sizes[policy][document];
                java.util.Arrays.sort(times[policy][document]);
                nanos += times[policy][document][1];
            }
            csv.append(
                    String.format(
                            Locale.ROOT,
                            "%s,%d,%d,%d,%d,%.3f,%.3f%n",
                            policies.get(policy),
                            inputs.size(),
                            changed[policy],
                            sourceBytes,
                            bytes,
                            (sourceBytes - bytes) * 100.0 / sourceBytes,
                            nanos / 1e6));
        }
        Files.createDirectories(Path.of("build/benchmarks"));
        Files.writeString(Path.of("build/benchmarks/brotli-compression.csv"), csv);
        System.out.print(csv);
    }
}
