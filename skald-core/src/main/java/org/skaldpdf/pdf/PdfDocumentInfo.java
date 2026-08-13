package org.skaldpdf.pdf;

import java.time.Instant;

/** Document metadata written to the XMP metadata stream. */
public final class PdfDocumentInfo {
    private String title;
    private String author;
    private String subject;
    private String keywords;
    private Instant creationDate;
    private Instant modificationDate;

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

    public PdfDocumentInfo setCreationDate(Instant value) {
        creationDate = value;
        return this;
    }

    public Instant getCreationDate() {
        return creationDate;
    }

    public PdfDocumentInfo setModificationDate(Instant value) {
        modificationDate = value;
        return this;
    }

    public Instant getModificationDate() {
        return modificationDate;
    }
}
