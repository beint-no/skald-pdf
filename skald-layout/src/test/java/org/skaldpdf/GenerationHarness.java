package org.skaldpdf;

import org.skaldpdf.reai.ReaiStyleDocuments;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Measures generation time and output size for the documents we actually ship.
 * Write {@code build/benchmarks/latest.json} after every change and compare
 * against the checked-in baseline.
 */
public final class GenerationHarness {
    public record Sample(String name, long nanos, int bytes, int pages) {
    }

    public record Report(List<Sample> samples, long totalNanos, int totalBytes, int iterations) {
        public Sample sample(String name) {
            return samples.stream().filter(sample -> sample.name().equals(name)).findFirst().orElseThrow();
        }
    }

    private GenerationHarness() {
    }

    public static Report run(int iterations) throws Exception {
        if (iterations < 1) {
            throw new IllegalArgumentException("Need at least one timed iteration");
        }
        var logo = PdfTestSupport.sampleLogo();
        var generators = generators(logo);
        for (var generator : generators.values()) {
            generator.get();
        }
        var totals = new LinkedHashMap<String, Acc>();
        generators.keySet().forEach(name -> totals.put(name, new Acc()));
        var started = System.nanoTime();
        for (int iteration = 0; iteration < iterations; iteration++) {
            for (var entry : generators.entrySet()) {
                var begin = System.nanoTime();
                var bytes = entry.getValue().get();
                var elapsed = System.nanoTime() - begin;
                try (var parsed = PdfTestSupport.load(bytes)) {
                    totals.get(entry.getKey()).add(elapsed, bytes.length, parsed.getNumberOfPages());
                }
            }
        }
        var wall = System.nanoTime() - started;
        var samples = new ArrayList<Sample>();
        for (var entry : totals.entrySet()) {
            var acc = entry.getValue();
            samples.add(new Sample(entry.getKey(), acc.nanos / iterations, acc.bytes / iterations, acc.pages / iterations));
        }
        samples.sort(Comparator.comparing(Sample::name));
        var totalBytes = samples.stream().mapToInt(Sample::bytes).sum();
        return new Report(List.copyOf(samples), wall, totalBytes, iterations);
    }

    public static void write(Path directory, Report report) throws IOException {
        Files.createDirectories(directory);
        Files.writeString(directory.resolve("latest.json"), toJson(report), StandardCharsets.UTF_8);
        Files.writeString(directory.resolve("latest.md"), toMarkdown(report), StandardCharsets.UTF_8);
        var downloads = Path.of(System.getProperty("user.home"), "Downloads", "skald-benchmarks");
        if (Files.isDirectory(downloads.getParent())) {
            Files.createDirectories(downloads);
            Files.writeString(downloads.resolve("latest.json"), toJson(report), StandardCharsets.UTF_8);
            Files.writeString(downloads.resolve("latest.md"), toMarkdown(report), StandardCharsets.UTF_8);
        }
    }

    public static Map<String, Integer> readBaseline(Path path) throws IOException {
        var json = Files.readString(path);
        var result = new LinkedHashMap<String, Integer>();
        var matcher = java.util.regex.Pattern.compile("\"name\"\\s*:\\s*\"([^\"]+)\".*?\"bytes\"\\s*:\\s*(\\d+)",
            java.util.regex.Pattern.DOTALL).matcher(json);
        while (matcher.find()) {
            result.put(matcher.group(1), Integer.parseInt(matcher.group(2)));
        }
        return result;
    }

    private static Map<String, java.util.function.Supplier<byte[]>> generators(byte[] logo) {
        var generators = new LinkedHashMap<String, java.util.function.Supplier<byte[]>>();
        generators.put("reai-invoice", () -> ReaiStyleDocuments.invoice(ReaiStyleDocuments.sampleInvoice(), logo));
        generators.put("reai-credit-note", () -> ReaiStyleDocuments.invoice(ReaiStyleDocuments.creditNote(), logo));
        generators.put("reai-paid-copy", () -> ReaiStyleDocuments.invoice(ReaiStyleDocuments.paidCopy(), logo));
        generators.put("reai-long-invoice", () -> ReaiStyleDocuments.invoice(ReaiStyleDocuments.longInvoice(), logo));
        generators.put("reai-reminder", () -> ReaiStyleDocuments.reminder(false, logo));
        generators.put("reai-packing-slip", () -> ReaiStyleDocuments.packingSlip(logo));
        generators.put("reai-respiro", () -> ReaiStyleDocuments.invoice(ReaiStyleDocuments.respiroPaidCopy(), logo));
        TypicalBusinessDocuments.all(logo).forEach((name, document) ->
            generators.put("typical-" + name, document.generator()));
        return generators;
    }

    private static String toJson(Report report) {
        var json = new StringBuilder(256 + report.samples().size() * 80);
        json.append("{\n  \"iterations\": ").append(report.iterations())
            .append(",\n  \"totalNanos\": ").append(report.totalNanos())
            .append(",\n  \"totalBytes\": ").append(report.totalBytes())
            .append(",\n  \"samples\": [\n");
        for (int index = 0; index < report.samples().size(); index++) {
            var sample = report.samples().get(index);
            json.append("    {\"name\": \"").append(sample.name())
                .append("\", \"nanos\": ").append(sample.nanos())
                .append(", \"bytes\": ").append(sample.bytes())
                .append(", \"pages\": ").append(sample.pages()).append('}');
            if (index + 1 < report.samples().size()) {
                json.append(',');
            }
            json.append('\n');
        }
        return json.append("  ]\n}\n").toString();
    }

    private static String toMarkdown(Report report) {
        var md = new StringBuilder();
        md.append("# Skald generation harness\n\n");
        md.append(String.format(Locale.ROOT, "Iterations: %d. Wall time: %.1f ms. Corpus size: %d bytes.\n\n",
            report.iterations(), report.totalNanos() / 1_000_000.0, report.totalBytes()));
        md.append("| Document | Pages | Bytes | ms |\n|---|---:|---:|---:|\n");
        for (var sample : report.samples()) {
            md.append(String.format(Locale.ROOT, "| %s | %d | %d | %.2f |\n",
                sample.name(), sample.pages(), sample.bytes(), sample.nanos() / 1_000_000.0));
        }
        return md.toString();
    }

    private static final class Acc {
        private long nanos;
        private int bytes;
        private int pages;

        void add(long elapsed, int size, int pageCount) {
            nanos += elapsed;
            bytes += size;
            pages += pageCount;
        }
    }
}
