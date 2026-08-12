package no.beint.skald.event;

import no.beint.skald.pdf.PdfDocument;
import no.beint.skald.pdf.PdfPage;

public final class PdfDocumentEvent extends AbstractPdfDocumentEvent {
    public static final String END_PAGE = "end-page";

    private final PdfDocument document;
    private final PdfPage page;

    public PdfDocumentEvent(PdfDocument document, PdfPage page) {
        this.document = document;
        this.page = page;
    }

    public PdfDocument getDocument() {
        return document;
    }

    public PdfPage getPage() {
        return page;
    }
}
