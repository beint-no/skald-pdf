package org.skaldpdf.invoice.no;

import java.util.Objects;

/** Payment destination printed in the invoice payment block. */
public record Bank(String name, String account, String iban, String bic) {
    public Bank {
        name = Objects.requireNonNullElse(name, "").strip();
        account = Objects.requireNonNullElse(account, "").replace(" ", "");
        iban = Objects.requireNonNullElse(iban, "").replace(" ", "").toUpperCase();
        bic = Objects.requireNonNullElse(bic, "").strip().toUpperCase();
    }

    public boolean hasPaymentDestination() {
        return !account.isEmpty() || !iban.isEmpty();
    }
}
