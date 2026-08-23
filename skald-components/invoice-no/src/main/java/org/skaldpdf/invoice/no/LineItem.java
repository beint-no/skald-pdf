package org.skaldpdf.invoice.no;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * One invoice line. Amounts are computed: {@code qty × unit × (1 − discount)}
 * exclusive of VAT, then VAT at {@code vatRate} percent. Quantity {@code 0}
 * is a text-only line (description/comment, no amounts).
 */
public record LineItem(
    String description,
    String comment,
    BigDecimal quantity,
    BigDecimal unitPriceExVat,
    BigDecimal discountPercent,
    BigDecimal vatRate
) {
    public LineItem {
        description = Company.requireText(description, "description");
        comment = Company.optionalText(comment);
        quantity = Objects.requireNonNull(quantity, "quantity");
        if (quantity.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("quantity must not be negative");
        }
        unitPriceExVat = Objects.requireNonNull(unitPriceExVat, "unitPriceExVat");
        vatRate = Objects.requireNonNull(vatRate, "vatRate");
        if (vatRate.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("vatRate must not be negative");
        }
        discountPercent = discountPercent == null ? BigDecimal.ZERO : discountPercent;
        if (discountPercent.compareTo(BigDecimal.ZERO) < 0
            || discountPercent.compareTo(new BigDecimal("100")) > 0) {
            throw new IllegalArgumentException("discountPercent must be between 0 and 100");
        }
    }

    public LineItem(String description, String comment, BigDecimal quantity,
                    BigDecimal unitPriceExVat, BigDecimal vatRate) {
        this(description, comment, quantity, unitPriceExVat, BigDecimal.ZERO, vatRate);
    }

    public boolean textOnly() {
        return quantity.compareTo(BigDecimal.ZERO) == 0;
    }

    public boolean hasDiscount() {
        return discountPercent.compareTo(BigDecimal.ZERO) > 0;
    }

    public BigDecimal amountExVat() {
        var raw = quantity.multiply(unitPriceExVat);
        if (hasDiscount()) {
            raw = raw.multiply(BigDecimal.ONE.subtract(discountPercent.movePointLeft(2)));
        }
        return NorwegianMoney.amount(raw);
    }

    public BigDecimal vatAmount() {
        return NorwegianMoney.amount(amountExVat().multiply(vatRate).movePointLeft(2));
    }

    public BigDecimal amountIncVat() {
        return amountExVat().add(vatAmount());
    }
}
