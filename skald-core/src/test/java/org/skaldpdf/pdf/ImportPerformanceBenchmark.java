package org.skaldpdf.pdf;

import com.sun.management.ThreadMXBean;
import org.skaldpdf.codec.RasterImages;
import org.skaldpdf.geom.PageSize;
import org.skaldpdf.image.ImageData;

import java.io.ByteArrayOutputStream;
import java.lang.management.ManagementFactory;
import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Random;
import java.util.function.LongSupplier;

/** Opt-in allocation/throughput probe; synthetic data, no native codecs or CI timing gates. */
public final class ImportPerformanceBenchmark {
    private static volatile long consumed;

    private ImportPerformanceBenchmark() {
    }

    public static void main(String[] args) {
        var bean = ManagementFactory.getPlatformMXBean(ThreadMXBean.class);
        if (bean == null || !bean.isThreadAllocatedMemorySupported()) {
            throw new IllegalStateException("Thread allocation counters are required");
        }
        bean.setThreadAllocatedMemoryEnabled(true);
        var samples = new byte[1024 * 1024 * 3];
        new Random(42).nextBytes(samples);
        var image = RasterImages.asJpeg(ImageData.fromRgb(1024, 1024, samples), 0.85f);
        var output = new ByteArrayOutputStream();
        try (var document = new PdfDocument(new PdfWriter(output))) {
            for (int page = 0; page < 32; page++) {
                image.drawOn(document, document.addNewPage(PageSize.A4), 10, 10, 400, 400);
            }
        }
        var source = output.toByteArray();
        System.out.printf(Locale.ROOT, "# pages=32, shared_jpeg_bytes=%d, pdf_bytes=%d%n",
            image.samples().length, source.length);
        System.out.println("scenario,round,ns_per_operation,allocated_bytes_per_operation,operations");
        try (var document = new PdfDocument(new PdfReader(source))) {
            measure(bean, "inspect-shared-image", () -> document.importedImages().getFirst().encodedLength());
        }
        var parser = new NativePdfParser(source);
        measure(bean, "semantic-digest", () -> parser.semanticDigest(new IdentityHashMap<>())[0]);
        measure(bean, "canonical-rewrite", () -> {
            var target = new ByteArrayOutputStream(source.length);
            try (var document = new PdfDocument(new PdfReader(source), new PdfWriter(target))) {
                consumed = document.getNumberOfPages();
            }
            return target.size();
        });
    }

    private static void measure(ThreadMXBean bean, String name, LongSupplier operation) {
        var warmupEnd = System.nanoTime() + 1_000_000_000L;
        do {
            consumed = operation.getAsLong();
        } while (System.nanoTime() < warmupEnd);
        for (int round = 1; round <= 3; round++) {
            var thread = Thread.currentThread().threadId();
            var allocated = bean.getThreadAllocatedBytes(thread);
            var start = System.nanoTime();
            long operations = 0;
            do {
                consumed = operation.getAsLong();
                operations++;
            } while (System.nanoTime() - start < 1_000_000_000L);
            var elapsed = System.nanoTime() - start;
            allocated = bean.getThreadAllocatedBytes(thread) - allocated;
            System.out.printf(Locale.ROOT, "%s,%d,%.1f,%.1f,%d%n",
                name, round, elapsed / (double) operations, allocated / (double) operations, operations);
        }
    }
}
