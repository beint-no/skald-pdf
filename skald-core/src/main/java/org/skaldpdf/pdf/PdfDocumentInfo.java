package org.skaldpdf.pdf;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Objects;

/** Document metadata written to the XMP metadata stream. */
public final class PdfDocumentInfo {
    private String title = "";
    private String author = "";
    private String subject = "";
    private String keywords = "";
    private @Nullable Instant creationDate;
    private @Nullable Instant modificationDate;

    PdfDocumentInfo() {
    }

    public PdfDocumentInfo setTitle(String value) {
        title = Objects.requireNonNull(value, "value").strip();
        return this;
    }

    public String getTitle() {
        return title;
    }

    public PdfDocumentInfo setAuthor(String value) {
        author = Objects.requireNonNull(value, "value").strip();
        return this;
    }

    public String getAuthor() {
        return author;
    }

    public PdfDocumentInfo setSubject(String value) {
        subject = Objects.requireNonNull(value, "value").strip();
        return this;
    }

    public String getSubject() {
        return subject;
    }

    public PdfDocumentInfo setKeywords(String value) {
        keywords = Objects.requireNonNull(value, "value").strip();
        return this;
    }

    public String getKeywords() {
        return keywords;
    }

    public PdfDocumentInfo setCreationDate(Instant value) {
        creationDate = Objects.requireNonNull(value, "value");
        return this;
    }

    public @Nullable Instant getCreationDate() {
        return creationDate;
    }

    public PdfDocumentInfo setModificationDate(Instant value) {
        modificationDate = Objects.requireNonNull(value, "value");
        return this;
    }

    public @Nullable Instant getModificationDate() {
        return modificationDate;
    }
}
