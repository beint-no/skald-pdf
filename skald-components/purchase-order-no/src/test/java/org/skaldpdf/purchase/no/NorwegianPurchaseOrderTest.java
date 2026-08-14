package org.skaldpdf.purchase.no;

import org.junit.jupiter.api.Test;
import org.skaldpdf.Pdf;
import org.skaldpdf.invoice.no.Company;
import org.skaldpdf.invoice.no.Party;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertTrue;

class NorwegianPurchaseOrderTest {
    @Test
    void purchaseOrderShowsSupplierAndShipTo() {
        var bytes = NorwegianPurchaseOrder.pdf(NorwegianPurchaseOrder.Model.builder()
            .company(new Company("Nordlys Handel AS", "NO", "999888777",
                "Storgata 10, 0184 Oslo, Norge", true))
            .supplier(new Party("Papirgrossisten AS", "Industriveien 2", "2000 Lillestrøm"))
            .shipTo(new Party("Nordlys lager", "Storgata 10", "0184 Oslo"))
            .number("PO-2201")
            .orderDate(LocalDate.of(2026, 8, 10))
            .neededBy(LocalDate.of(2026, 8, 20))
            .reference("Kari Nord")
            .notes("Leveres på bakdøren før kl. 12.")
            .line("Kopipapir A4", "PAP-A4", 10, "49.00", 25)
            .line("Konvolutter C5", "ENV-C5", 5, "12.00", 25)
            .build());
        assertTrue(new String(bytes, 0, 8, StandardCharsets.US_ASCII).startsWith("%PDF-2.0"));
        var text = Pdf.extractText(bytes);
        assertTrue(text.contains("Innkjøpsordre"));
        assertTrue(text.contains("PO-2201"));
        assertTrue(text.contains("Papirgrossisten AS"));
        assertTrue(text.contains("Leveres til"));
        assertTrue(text.contains("Nordlys lager"));
        assertTrue(text.contains("Totalsum"));
        assertTrue(text.contains("bakdøren"));
    }
}
