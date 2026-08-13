package org.skaldpdf.layout;

/** One-based page position known when running headers and footers are painted. */
public record PageNumbering(int pageNumber, int pageCount) {
    public PageNumbering {
        if (pageNumber < 1 || pageCount < 1 || pageNumber > pageCount) {
            throw new IllegalArgumentException("Page numbering must be one-based and within the document");
        }
    }
}
