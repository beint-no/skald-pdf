package org.skaldpdf.pdf;

import java.util.Objects;

/** A named in-document destination used by GoTo links and outlines. */
public record NamedDestination(String name, int pageNumber, float top) {
    public NamedDestination {
        Objects.requireNonNull(name, "name");
        if (name.isBlank() || name.codePoints().anyMatch(code -> code < 0x20 || code > 0x7e)) {
            throw new IllegalArgumentException("Destination names must be printable ASCII");
        }
        if (pageNumber < 1) {
            throw new IllegalArgumentException("Destination page numbers are one-based");
        }
        if (!Float.isFinite(top)) {
            throw new IllegalArgumentException("Destination top must be finite");
        }
    }
}
