package no.beint.skald.pdf.canvas;

import no.beint.skald.pdf.PdfContentStream;
import no.beint.skald.pdf.PdfDocument;
import no.beint.skald.pdf.PdfPage;
import no.beint.skald.pdf.PdfResources;
import no.beint.skald.pdf.extgstate.PdfExtGState;

import java.util.Objects;

public final class PdfCanvas {
    private final PdfDocument document;
    private final PdfPage page;
    private float fillOpacity = 1f;

    public PdfCanvas(PdfContentStream stream, PdfResources resources, PdfDocument document) {
        this.page = Objects.requireNonNull(stream, "stream").page();
        this.document = Objects.requireNonNull(document, "document");
        Objects.requireNonNull(resources, "resources");
    }

    public PdfCanvas saveState() {
        return this;
    }

    public PdfCanvas restoreState() {
        return this;
    }

    public PdfCanvas setExtGState(PdfExtGState state) {
        fillOpacity = Objects.requireNonNull(state, "state").fillOpacity();
        return this;
    }

    public PdfDocument document() {
        return document;
    }

    public PdfPage page() {
        return page;
    }

    public float fillOpacity() {
        return fillOpacity;
    }
}
