package org.skaldpdf.invoice.no;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    void treatsZeroQuantityAsTextOnly() {
        var line = new LineItem("Merknad", "Ingen vare", BigDecimal.ZERO,
            BigDecimal.ZERO, new BigDecimal("25"));
        assertTrue(line.textOnly());
        assertEquals(new BigDecimal("0.00"), line.amountExVat());
        assertEquals(new BigDecimal("0.00"), line.amountIncVat());
    }

    @Test
    void treatsMissingDiscountAsZero() {
        var line = new LineItem("Timer", "", BigDecimal.ONE,
            new BigDecimal("100.00"), null, new BigDecimal("25"));
        assertEquals(BigDecimal.ZERO, line.discountPercent());
        assertFalse(line.hasDiscount());
    }

    @Test
    void rejectsNegativeQuantityAndVat() {
        assertThrows(IllegalArgumentException.class, () -> new LineItem("x", "",
            new BigDecimal("-1"), new BigDecimal("1.00"), new BigDecimal("25")));
        assertThrows(IllegalArgumentException.class, () -> new LineItem("x", "",
            BigDecimal.ONE, new BigDecimal("1.00"), new BigDecimal("-1")));
    }
}
