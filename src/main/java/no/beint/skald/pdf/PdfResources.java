package no.beint.skald.pdf;

public final class PdfResources {
    private final PdfPage page;

    PdfResources(PdfPage page) {
        this.page = page;
    }

    PdfPage page() {
        return page;
    }
}
