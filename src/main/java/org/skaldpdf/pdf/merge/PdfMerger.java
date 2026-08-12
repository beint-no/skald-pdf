package org.skaldpdf.pdf.merge;

import org.skaldpdf.pdf.PdfDocument;

import java.util.Objects;

/** Copies parsed page trees into a new PDF 2.0 document. */
public final class PdfMerger {
    private final PdfDocument target;

    public PdfMerger(PdfDocument target) {
        this.target = Objects.requireNonNull(target, "target");
    }

    public PdfMerger merge(PdfDocument source, int fromPage, int toPage) {
        target.copyPagesFrom(Objects.requireNonNull(source, "source"), fromPage, toPage);
        return this;
    }
}
