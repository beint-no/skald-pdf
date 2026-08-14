package org.skaldpdf.invoice.no;

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
        country = requireText(country, "country").toUpperCase();
        organizationNumber = requireText(organizationNumber, "organizationNumber")
            .replace(" ", "");
        addressLine = Objects.requireNonNullElse(addressLine, "");
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
}
