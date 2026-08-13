package org.skaldpdf.pdf;

import java.util.Objects;

/** A single PDF outline (bookmark) targeting a one-based page number. */
public record OutlineItem(String title, int pageNumber) {
    public OutlineItem {
        Objects.requireNonNull(title, "title");
        if (title.isBlank()) {
            throw new IllegalArgumentException("Outline title must not be blank");
        }
        if (pageNumber < 1) {
            throw new IllegalArgumentException("Outline page numbers are one-based");
        }
    }
}
