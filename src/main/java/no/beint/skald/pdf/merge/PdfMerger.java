package no.beint.skald.pdf.merge;

import no.beint.skald.pdf.PdfDocument;

import java.io.IOException;
import java.util.Objects;

public final class PdfMerger {
    private final PdfDocument target;

    public PdfMerger(PdfDocument target) {
        this.target = Objects.requireNonNull(target, "target");
    }

    public PdfMerger merge(PdfDocument source, int fromPage, int toPage) {
        Objects.requireNonNull(source, "source");
        if (fromPage < 1 || toPage < fromPage || toPage > source.getNumberOfPages()) {
            throw new IllegalArgumentException("Invalid page range");
        }
        try {
            for (int pageNumber = fromPage; pageNumber <= toPage; pageNumber++) {
                target.backingDocument().importPage(source.backingDocument().getPage(pageNumber - 1));
            }
            return this;
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to merge PDF pages", exception);
        }
    }
}
