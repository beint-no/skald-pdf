package org.skaldpdf;

import com.sun.management.ThreadMXBean;
import org.skaldpdf.fonts.SkaldSans;
import org.skaldpdf.labels.ProductSticker;
import org.skaldpdf.layout.element.Cell;
import org.skaldpdf.layout.element.Paragraph;
import org.skaldpdf.layout.element.Table;
import org.skaldpdf.pdf.PdfDocument;
import org.skaldpdf.pdf.PdfReader;
import org.skaldpdf.reai.ReaiStyleDocuments;

import java.lang.management.ManagementFactory;
import java.util.Arrays;
import java.util.Locale;
import java.util.function.LongSupplier;

/** Opt-in, single-thread throughput/allocation probe; never a wall-clock CI assertion. */
public final class PerformanceBenchmark {
    private static volatile long consumed;

    private PerformanceBenchmark() {
    }

    public static void main(String[] args) throws Exception {
        var bean = ManagementFactory.getPlatformMXBean(ThreadMXBean.class);
        if (bean == null || !bean.isThreadAllocatedMemorySupported()) {
            throw new IllegalStateException("This benchmark requires thread allocation counters");
        }
        bean.setThreadAllocatedMemoryEnabled(true);
        var font = SkaldSans.regular();
        var labels = new String[] { "Invoice 2026-1001", "Til betaling: 1 250,00 NOK",
            "Blåbær, størrelse Ø", "Modern accounting service" };
        var logo = PdfTestSupport.sampleLogo();
        var invoice = ReaiStyleDocuments.sampleInvoice();
        var sticker = new ProductSticker.Spec("SOJA-BA-L", "CN", "Softy Jacket", "L", "",
            "80%Nylon, 20%Lycra", "8123613319580", "Orchid");
        var report = report();
        System.out.println("scenario,round,ns_per_operation,allocated_bytes_per_operation,operations");
        measure(bean, args, "text-width", 256, () -> {
            long total = 0;
            for (int i = 0; i < 256; i++) {
                total += Float.floatToIntBits(font.getWidth(labels[i % labels.length], 10));
            }
            return total;
        });
        measure(bean, args, "reai-invoice", 1, () -> ReaiStyleDocuments.invoice(invoice, logo).length);
        measure(bean, args, "ecomtools-sticker", 1, () -> ProductSticker.pdf(sticker).length);
        measure(bean, args, "report-1000-rows", 1, () -> report().length);
        measure(bean, args, "parse-report", 1, () -> {
            try (var document = new PdfDocument(new PdfReader(report))) {
                return document.getNumberOfPages();
            }
        });
        measure(bean, args, "extract-report", 1, () -> Pdf.extractText(report).length());
    }

    private static void measure(ThreadMXBean bean, String[] scenarios, String name, int batch, LongSupplier operation) {
        if (scenarios.length > 0 && Arrays.stream(scenarios).noneMatch(name::equals)) {
            return;
        }
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
                operations += batch;
            } while (System.nanoTime() - start < 1_000_000_000L);
            var elapsed = System.nanoTime() - start;
            allocated = bean.getThreadAllocatedBytes(thread) - allocated;
            System.out.printf(Locale.ROOT, "%s,%d,%.1f,%.1f,%d%n",
                name, round, elapsed / (double) operations, allocated / (double) operations, operations);
        }
    }

    private static byte[] report() {
        return Pdf.create(document -> {
            document.add(new Paragraph("Large report").bold().setFontSize(18));
            var table = new Table(new float[] { 1, 4, 2 }).useAllAvailableWidth();
            table.addHeaderCell(cell("#"));
            table.addHeaderCell(cell("Description"));
            table.addHeaderCell(cell("Amount"));
            for (int i = 0; i < 1_000; i++) {
                table.addCell(cell(Integer.toString(i)));
                table.addCell(cell("Row " + i + " generated efficiently on JDK 26"));
                table.addCell(cell("1 250.00"));
            }
            document.add(table);
        });
    }

    private static Cell cell(String text) {
        return new Cell().add(new Paragraph(text).setFontSize(8)).setPadding(3);
    }
}
