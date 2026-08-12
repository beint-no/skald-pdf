package no.beint.skald.pdf;

import org.apache.pdfbox.pdmodel.PDDocumentInformation;

public final class PdfDocumentInfo {
    private final PDDocumentInformation information;

    PdfDocumentInfo(PDDocumentInformation information) {
        this.information = information;
    }

    public PdfDocumentInfo setTitle(String value) {
        information.setTitle(value);
        return this;
    }

    public String getTitle() {
        return information.getTitle();
    }

    public PdfDocumentInfo setAuthor(String value) {
        information.setAuthor(value);
        return this;
    }

    public String getAuthor() {
        return information.getAuthor();
    }
}
