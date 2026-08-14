package org.skaldpdf.invoice.no;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;

/**
 * Money, dates, and account numbers as they appear on ReAI-style Norwegian
 * documents: US grouping ({@code 12,500.00}), {@code dd.MM.yyyy}, and
 * four-character IBAN chunks.
 */
public final class NorwegianMoney {
    public static final String NOK = "NOK";
    public static final RoundingMode ROUNDING = RoundingMode.HALF_UP;
    static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private NorwegianMoney() {
    }

    public static BigDecimal amount(BigDecimal value) {
        return Objects.requireNonNull(value, "amount").setScale(2, ROUNDING);
    }

    /**
     * Parses {@code 1,250.00}, {@code 1250.00}, or {@code 1250}. Commas are
     * thousands separators, matching the printed form.
     */
    public static BigDecimal parse(String value) {
        var text = Company.requireText(value, "amount").replace(" ", "").replace(",", "");
        try {
            return new BigDecimal(text).setScale(2, ROUNDING);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Not a money amount: " + value, exception);
        }
    }

    public static String format(BigDecimal amount) {
        var rounded = amount(amount);
        var symbols = DecimalFormatSymbols.getInstance(Locale.US);
        var format = new DecimalFormat("#,##0.00", symbols);
        format.setRoundingMode(ROUNDING);
        return format.format(rounded);
    }

    public static String format(String currency, BigDecimal amount) {
        var code = currency == null || currency.isBlank() ? NOK : currency.strip();
        return code + " " + format(amount);
    }

    public static String quantity(BigDecimal quantity) {
        var value = Objects.requireNonNull(quantity, "quantity").stripTrailingZeros();
        if (value.scale() <= 0) {
            return value.toPlainString();
        }
        var symbols = DecimalFormatSymbols.getInstance(Locale.US);
        var format = new DecimalFormat("#,##0.##", symbols);
        format.setRoundingMode(ROUNDING);
        return format.format(value);
    }

    public static String percent(BigDecimal rate) {
        var value = Objects.requireNonNull(rate, "rate").stripTrailingZeros();
        return (value.scale() <= 0 ? value.toPlainString() : value.toPlainString()) + " %";
    }

    public static String date(LocalDate date) {
        return Objects.requireNonNull(date, "date").format(DATE);
    }

    /** Groups characters in fours from the left: {@code NO93 1503 4567 890}. */
    public static String chunked(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        var compact = value.replace(" ", "");
        var result = new StringBuilder(compact.length() + compact.length() / 4);
        for (int index = 0; index < compact.length(); index++) {
            if (index > 0 && index % 4 == 0) {
                result.append(' ');
            }
            result.append(compact.charAt(index));
        }
        return result.toString();
    }
}
