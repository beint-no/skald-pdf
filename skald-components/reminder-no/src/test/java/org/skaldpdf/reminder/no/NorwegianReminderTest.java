package org.skaldpdf.reminder.no;

import org.junit.jupiter.api.Test;
import org.skaldpdf.Pdf;
import org.skaldpdf.invoice.no.Company;
import org.skaldpdf.invoice.no.Party;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NorwegianReminderTest {
    @Test
    void reminderIncludesLateFeeAndInterest() {
        var model = sample().kind(NorwegianReminder.Kind.REMINDER).build();
        assertEquals(new BigDecimal("15777.47"), model.payable());
        var text = Pdf.extractText(NorwegianReminder.pdf(model));
        assertTrue(new String(NorwegianReminder.pdf(model), 0, 8, StandardCharsets.US_ASCII).startsWith("%PDF-2.0"));
        assertTrue(text.contains("Purring"));
        assertTrue(text.contains("Purregebyr"));
        assertTrue(text.contains("Renter 12 %"));
        assertTrue(text.contains("Til betaling"));
        assertTrue(text.contains("NOK 15,777.47"));
    }

    @Test
    void collectionNoticeUsesEnforcementActText() {
        var text = Pdf.extractText(NorwegianReminder.pdf(sample()
            .kind(NorwegianReminder.Kind.COLLECTION)
            .build()));
        assertTrue(text.contains("Betalingsoppfordring"));
        assertTrue(text.contains("tvangsfullbyrdelsesloven"));
        assertTrue(text.contains("14 dager"));
    }

    private static NorwegianReminder.Builder sample() {
        return NorwegianReminder.Model.builder()
            .company(new Company("Nordlys Handel AS", "NO", "999888777",
                "Storgata 10, 0184 Oslo, Norge", true))
            .customer(new Party("Fjordbutikken AS", "Kaien 4", "5003 Bergen"))
            .invoiceNumber("1001")
            .invoiceDate(LocalDate.of(2026, 8, 12))
            .dueDate(LocalDate.of(2026, 8, 26))
            .noticeDate(LocalDate.of(2026, 9, 9))
            .originalAmount("15,625.00")
            .lateFee("70.00")
            .interest("82.47", "12", 14);
    }
}
