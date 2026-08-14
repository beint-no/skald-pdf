package org.skaldpdf.invoice.no;

import java.util.List;
import java.util.Objects;

/** Customer, supplier, or ship-to on a Norwegian commercial document. */
public record Party(String name, List<String> addressLines) {
    public Party {
        name = Company.requireText(name, "name");
        addressLines = List.copyOf(Objects.requireNonNullElse(addressLines, List.of()));
    }

    public Party(String name, String... addressLines) {
        this(name, List.of(addressLines));
    }
}
