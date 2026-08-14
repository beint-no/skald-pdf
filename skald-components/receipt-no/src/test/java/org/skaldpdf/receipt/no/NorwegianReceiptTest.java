package org.skaldpdf.receipt.no;

import org.apache.pdfbox.Loader;
import org.junit.jupiter.api.Test;
import org.skaldpdf.Pdf;
import org.skaldpdf.invoice.no.Company;
import org.skaldpdf.invoice.no.Party;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NorwegianReceiptTest {
    @Test
    void writesAnA5ReceiptWithVatTotal() throws Exception {
        var bytes = NorwegianReceipt.pdf(NorwegianReceipt.Model.builder()
            .company(new Company("Nordlys Handel AS", "NO", "999888777",
                "Storgata 10, 0184 Oslo, Norge", true))
            .customer(new Party("Kari Nord", "Oslo"))
            .number("K-4401")
            .issuedAt(LocalDateTime.of(2026, 8, 14, 14, 30))
            .paymentMethod("Kort")
            .line("Kaffe", 2, "45.00", 25)
            .line("Bolle", 1, "32.00", 15)
            .build());
        assertTrue(new String(bytes, 0, 8, StandardCharsets.US_ASCII).startsWith("%PDF-2.0"));
        try (var parsed = Loader.loadPDF(bytes)) {
            var box = parsed.getPage(0).getMediaBox();
            assertEquals(NorwegianReceipt.PAGE_SIZE.getWidth(), box.getWidth(), 0.05);
            assertEquals(NorwegianReceipt.PAGE_SIZE.getHeight(), box.getHeight(), 0.05);
        }
        var text = Pdf.extractText(bytes);
        assertTrue(text.contains("Kvittering"));
        assertTrue(text.contains("K-4401"));
        assertTrue(text.contains("Å betale"));
        assertTrue(text.contains("Betalt med Kort"));
        assertTrue(text.contains("MVA"));
    }
}
