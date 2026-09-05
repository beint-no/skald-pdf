package org.skaldpdf.pdf;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PdfNumberParsingTest {
    @Test
    void acceptsSignedIntegersAndPdfDecimalFormsInObjectsAndContent() {
        var bytes = fixture("+300.", "+0");
        try (var document = new PdfDocument(new PdfReader(bytes))) {
            assertEquals(1, document.getNumberOfPages());
            assertEquals(300.5f, document.getPage(1).getPageSize().getWidth());
        }
        assertEquals("First\nSecond", PdfText.extract(bytes));
    }

    @Test
    void rejectsInvalidNumbersAndNonIntegerRotations() {
        for (var token : List.of(".", "+", "--1", "1.2.3", "1e2", "NaN", "Infinity")) {
            assertThrows(IllegalArgumentException.class, () -> new NativePdfParser(fixture(token, "0")), token);
        }
        for (var token : List.of("0.0", ".0", "0e0", "99999999999999999999")) {
            assertThrows(IllegalArgumentException.class, () -> new NativePdfParser(fixture("300", token)), token);
        }
    }

    private static byte[] fixture(String right, String rotation) {
        var content = "BT /F1 +12. Tf 1 0 0 1 .5 +180. Tm (First) Tj 0 -20.5 Td (Second) Tj ET\n";
        var objects = List.of(
            "<< /Type /Catalog /Pages 2 0 R >>",
            "<< /Type /Pages /Count +1 /Kids [3 0 R] >>",
            "<< /Type /Page /Parent 2 0 R /MediaBox [-.5 -0.0 " + right + " 200.] "
                + "/Rotate " + rotation + " /Resources << /Font << /F1 5 0 R >> >> /Contents 4 0 R >>",
            "<< /Length " + content.length() + " >>\nstream\n" + content + "endstream",
            "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>"
        );
        var output = new ByteArrayOutputStream();
        write(output, "%PDF-1.7\n");
        var offsets = new int[objects.size()];
        for (int i = 0; i < objects.size(); i++) {
            offsets[i] = output.size();
            write(output, (i + 1) + " 0 obj\n" + objects.get(i) + "\nendobj\n");
        }
        var xref = output.size();
        write(output, "xref\n0 6\n0000000000 65535 f \n");
        for (var offset : offsets) {
            write(output, String.format(Locale.ROOT, "%010d 00000 n \n", offset));
        }
        write(output, "trailer\n<< /Size 6 /Root 1 0 R >>\nstartxref\n" + xref + "\n%%EOF\n");
        return output.toByteArray();
    }

    private static void write(ByteArrayOutputStream output, String value) {
        output.writeBytes(value.getBytes(StandardCharsets.US_ASCII));
    }
}
