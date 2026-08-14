package org.skaldpdf.invoice.no;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LineItemTest {
    @Test
    void computesVatFromQuantityAndUnitPrice() {
        var line = new LineItem("Regnskap", "", new BigDecimal("8"),
            new BigDecimal("1250.00"), new BigDecimal("25"));
        assertEquals(new BigDecimal("10000.00"), line.amountExVat());
        assertEquals(new BigDecimal("2500.00"), line.vatAmount());
        assertEquals(new BigDecimal("12500.00"), line.amountIncVat());
    }

    @Test
    void appliesDiscountBeforeVat() {
        var line = new LineItem("Timer", "Avtalt", new BigDecimal("10"),
            new BigDecimal("1250.00"), new BigDecimal("10"), new BigDecimal("25"));
        assertEquals(new BigDecimal("11250.00"), line.amountExVat());
        assertEquals(new BigDecimal("2812.50"), line.vatAmount());
        assertEquals(new BigDecimal("14062.50"), line.amountIncVat());
    }

    @Test
    void keepsCreditLinesNegative() {
        var line = new LineItem("Kredit", "", new BigDecimal("8"),
            new BigDecimal("-1250.00"), new BigDecimal("25"));
        assertEquals(new BigDecimal("-10000.00"), line.amountExVat());
        assertEquals(new BigDecimal("-2500.00"), line.vatAmount());
        assertEquals(new BigDecimal("-12500.00"), line.amountIncVat());
    }

    @Test
    void rejectsZeroQuantityAndNegativeVat() {
        assertThrows(IllegalArgumentException.class, () -> new LineItem("x", "",
            BigDecimal.ZERO, new BigDecimal("1.00"), new BigDecimal("25")));
        assertThrows(IllegalArgumentException.class, () -> new LineItem("x", "",
            BigDecimal.ONE, new BigDecimal("1.00"), new BigDecimal("-1")));
    }
}
