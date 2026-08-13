package org.skaldpdf.pdf;

/** Document metadata written to the XMP metadata stream. */
public final class PdfDocumentInfo {
    private String title;
    private String author;
    private String subject;
    private String keywords;

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

    public PdfDocumentInfo setSubject(String value) {
        subject = value;
        return this;
    }

    public String getSubject() {
        return subject;
    }

    public PdfDocumentInfo setKeywords(String value) {
        keywords = value;
        return this;
    }

    public String getKeywords() {
        return keywords;
    }
}
