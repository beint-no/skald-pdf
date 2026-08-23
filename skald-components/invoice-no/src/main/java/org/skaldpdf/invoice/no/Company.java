package org.skaldpdf.invoice.no;

import org.jspecify.annotations.Nullable;

import java.util.Locale;
import java.util.Objects;

/**
 * Issuer or seller on a Norwegian commercial document.
 *
 * <p>{@code organizationNumber} is the nine-digit organisation number without
 * the {@code NO} prefix or {@code MVA} suffix. Those are added when the
 * company is VAT registered.
 */
public record Company(
    String name,
    String country,
    String organizationNumber,
    String addressLine,
    boolean vatRegistered
) {
    public Company {
        name = requireText(name, "name");
        country = requireText(country, "country").toUpperCase(Locale.ROOT);
        organizationNumber = requireText(organizationNumber, "organizationNumber")
            .replace(" ", "");
        addressLine = optionalText(addressLine);
    }

    /** {@code NO999888777MVA} when VAT registered. */
    public String formattedOrganizationNumber() {
        return country + organizationNumber + (vatRegistered ? "MVA" : "");
    }

    public static String requireText(String value, String field) {
        Objects.requireNonNull(value, field);
        var stripped = value.strip();
        if (stripped.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return stripped;
    }

    /** Missing or blank text becomes empty, never {@code null}. */
    public static String optionalText(@Nullable String value) {
        return value == null ? "" : value.strip();
    }
}
