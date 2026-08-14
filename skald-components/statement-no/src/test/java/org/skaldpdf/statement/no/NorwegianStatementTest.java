package org.skaldpdf.statement.no;

import org.junit.jupiter.api.Test;
import org.skaldpdf.Pdf;
import org.skaldpdf.invoice.no.Company;
import org.skaldpdf.invoice.no.Party;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NorwegianStatementTest {
    @Test
    void runningBalanceClosesAndLabelsAreNorwegian() {
        var model = NorwegianStatement.Model.builder()
            .company(new Company("Nordlys Handel AS", "NO", "999888777",
                "Storgata 10, 0184 Oslo, Norge", true))
            .customer(new Party("Fjordbutikken AS", "Kaien 4", "5003 Bergen"))
            .number("2026-08")
            .period(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31))
            .openingBalance("1,000.00")
            .debit(LocalDate.of(2026, 8, 12), "1001", "Faktura 1001", "15,625.00")
            .credit(LocalDate.of(2026, 8, 20), "BET", "Innbetaling", "15,625.00")
            .build();
        assertEquals(new BigDecimal("1000.00"), model.closingBalance());
        var bytes = NorwegianStatement.pdf(model);
        assertTrue(new String(bytes, 0, 8, StandardCharsets.US_ASCII).startsWith("%PDF-2.0"));
        var text = Pdf.extractText(bytes);
        assertTrue(text.contains("Kontooversikt"));
        assertTrue(text.contains("Inngående saldo"));
        assertTrue(text.contains("Utgående saldo"));
        assertTrue(text.contains("Faktura 1001"));
        assertTrue(text.contains("1,000.00"));
    }
}
