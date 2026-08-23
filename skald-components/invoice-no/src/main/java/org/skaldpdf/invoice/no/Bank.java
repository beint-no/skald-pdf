package org.skaldpdf.invoice.no;

import java.util.Locale;

/** Payment destination printed in the invoice payment block. */
public record Bank(String name, String account, String iban, String bic) {
    public Bank {
        name = Company.optionalText(name);
        account = Company.optionalText(account).replace(" ", "");
        iban = Company.optionalText(iban).replace(" ", "").toUpperCase(Locale.ROOT);
        bic = Company.optionalText(bic).toUpperCase(Locale.ROOT);
    }

    public boolean hasPaymentDestination() {
        return !account.isEmpty() || !iban.isEmpty();
    }
}
