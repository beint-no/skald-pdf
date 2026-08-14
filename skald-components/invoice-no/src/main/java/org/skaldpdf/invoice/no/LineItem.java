package org.skaldpdf.invoice.no;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * One invoice line. Amounts are computed: {@code qty × unit × (1 − discount)}
 * exclusive of VAT, then VAT at {@code vatRate} percent.
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
        comment = Objects.requireNonNullElse(comment, "").strip();
        quantity = Objects.requireNonNull(quantity, "quantity");
        if (quantity.compareTo(BigDecimal.ZERO) == 0) {
            throw new IllegalArgumentException("quantity must not be zero");
        }
        unitPriceExVat = Objects.requireNonNull(unitPriceExVat, "unitPriceExVat");
        vatRate = Objects.requireNonNull(vatRate, "vatRate");
        if (vatRate.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("vatRate must not be negative");
        }
        if (discountPercent != null) {
            if (discountPercent.compareTo(BigDecimal.ZERO) < 0
                || discountPercent.compareTo(new BigDecimal("100")) > 0) {
                throw new IllegalArgumentException("discountPercent must be between 0 and 100");
            }
        }
    }

    public LineItem(String description, String comment, BigDecimal quantity,
                    BigDecimal unitPriceExVat, BigDecimal vatRate) {
        this(description, comment, quantity, unitPriceExVat, null, vatRate);
    }

    public boolean hasDiscount() {
        return discountPercent != null && discountPercent.compareTo(BigDecimal.ZERO) > 0;
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
