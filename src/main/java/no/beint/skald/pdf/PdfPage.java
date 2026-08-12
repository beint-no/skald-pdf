package no.beint.skald.pdf;

import no.beint.skald.geom.PageSize;
import no.beint.skald.geom.Rectangle;
import org.apache.pdfbox.pdmodel.PDPage;

public final class PdfPage {
    private final PdfDocument document;
    private final PDPage page;

    PdfPage(PdfDocument document, PDPage page) {
        this.document = document;
        this.page = page;
    }

    public PageSize getPageSize() {
        var box = page.getMediaBox();
        return new PageSize(box.getWidth(), box.getHeight());
    }

    public Rectangle getCropBox() {
        var box = page.getCropBox();
        return new Rectangle(box.getLowerLeftX(), box.getLowerLeftY(), box.getWidth(), box.getHeight());
    }

    public PdfResources getResources() {
        return new PdfResources(this);
    }

    public PdfContentStream newContentStreamAfter() {
        return new PdfContentStream(this);
    }

    public void setIgnorePageRotationForContent(boolean ignored) {
    }

    public PdfDocument document() {
        return document;
    }

    public PDPage backingPage() {
        return page;
    }
}
