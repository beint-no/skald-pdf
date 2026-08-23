package org.skaldpdf.invoice.no;

import java.util.List;
import java.util.Objects;

/** Customer, supplier, or ship-to on a Norwegian commercial document. */
public record Party(String name, List<String> addressLines) {
    public Party {
        name = Company.requireText(name, "name");
        addressLines = Objects.requireNonNullElse(addressLines, List.<String>of()).stream()
            .filter(Objects::nonNull)
            .map(String::strip)
            .toList();
    }

    public Party(String name, String... addressLines) {
        this(name, List.of(addressLines));
    }
}
