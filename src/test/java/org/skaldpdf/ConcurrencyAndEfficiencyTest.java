package org.skaldpdf;

import org.skaldpdf.geom.PageSize;
import org.skaldpdf.layout.Document;
import org.skaldpdf.layout.element.Cell;
import org.skaldpdf.layout.element.Paragraph;
import org.skaldpdf.layout.element.Table;
import org.skaldpdf.layout.properties.UnitValue;
import org.skaldpdf.pdf.PdfDocument;
import org.skaldpdf.pdf.PdfWriter;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertTimeout;

class ConcurrencyAndEfficiencyTest {
    @Test
    void generatesIndependentDocumentsOnVirtualThreads() {
        assertTimeout(Duration.ofSeconds(15), () -> {
            var jobs = IntStream.range(0, 32)
                .<Callable<byte[]>>mapToObj(index -> () -> documentWithRows("Concurrent " + index, 12))
                .toList();

            try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                var results = executor.invokeAll(jobs);
                assertEquals(32, results.size());
                for (int index = 0; index < results.size(); index++) {
                    assertTrue(PdfTestSupport.text(results.get(index).get()).contains("Concurrent " + index));
                }
            }
        });
    }

    @Test
    void thousandRowReportStaysCompactAndFast() {
        assertTimeout(Duration.ofSeconds(15), () -> {
            var bytes = documentWithRows("Large report", 1_000);
            assertTrue(bytes.length < 250_000,
                () -> "1,000-row report should remain compact, actual bytes=" + bytes.length);
            try (var document = PdfTestSupport.load(bytes)) {
                assertTrue(document.getNumberOfPages() > 10);
            }
            assertTrue(PdfTestSupport.text(bytes).contains("Row 999"));
            assertTrue(new String(bytes, java.nio.charset.StandardCharsets.ISO_8859_1).contains("/ObjStm"),
                "PDF 2.0 object streams should compact small structural objects");
        });
    }

    private static byte[] documentWithRows(String title, int rows) {
        var output = new ByteArrayOutputStream();
        try (var pdf = new PdfDocument(new PdfWriter(output));
             var document = new Document(pdf, PageSize.A4)) {
            document.setMargins(32, 32, 32, 32);
            document.add(new Paragraph(title).bold().setFontSize(18));
            var table = new Table(UnitValue.createPercentArray(new float[] { 1, 4, 2 }))
                .useAllAvailableWidth();
            table.addHeaderCell(cell("#"));
            table.addHeaderCell(cell("Description"));
            table.addHeaderCell(cell("Amount"));
            for (int index = 0; index < rows; index++) {
                table.addCell(cell(Integer.toString(index)));
                table.addCell(cell("Row " + index + " generated efficiently on JDK 25"));
                table.addCell(cell("1 250.00"));
            }
            document.add(table);
        }
        return output.toByteArray();
    }

    private static Cell cell(String text) {
        return new Cell().add(new Paragraph(text).setFontSize(8)).setPadding(3);
    }
}
