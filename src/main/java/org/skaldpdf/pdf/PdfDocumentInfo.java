package org.skaldpdf.pdf;

/** Basic metadata written to the XMP metadata stream. */
public final class PdfDocumentInfo {
    private String title;
    private String author;

    PdfDocumentInfo() {
    }

    public PdfDocumentInfo setTitle(String value) {
        title = value;
        return this;
    }

    public String getTitle() {
        return title;
    }

    public PdfDocumentInfo setAuthor(String value) {
        author = value;
        return this;
    }

    public String getAuthor() {
        return author;
    }
}
