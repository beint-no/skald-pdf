package org.skaldpdf.layout;

import org.skaldpdf.geom.PageSize;
import org.skaldpdf.font.PdfFont;
import org.skaldpdf.font.PdfFontFactory;
import org.skaldpdf.layout.element.LayoutElement;
import org.skaldpdf.layout.internal.LayoutEngine;
import org.skaldpdf.pdf.PdfDocument;

import java.util.Objects;

public final class Document implements AutoCloseable {
    private final PdfDocument pdfDocument;
    private final PageSize pageSize;
    private float topMargin = 36f;
    private float rightMargin = 36f;
    private float bottomMargin = 36f;
    private float leftMargin = 36f;
    private PdfFont font = PdfFontFactory.regular();
    private float fontSize = 12f;
    private LayoutEngine layout;
    private LayoutElement pending;
    private boolean closed;

    public Document(PdfDocument pdfDocument) {
        this(pdfDocument, PageSize.A4);
    }

    public Document(PdfDocument pdfDocument, PageSize pageSize) {
        this.pdfDocument = Objects.requireNonNull(pdfDocument, "pdfDocument");
        this.pageSize = Objects.requireNonNull(pageSize, "pageSize");
    }

    public Document add(LayoutElement element) {
        if (closed) {
            throw new IllegalStateException("Document is closed");
        }
        var next = Objects.requireNonNull(element, "element");
        if (pending != null) {
            layout().render(pending, next);
        }
        pending = next;
        return this;
    }

    public void setMargins(float topMargin, float rightMargin, float bottomMargin, float leftMargin) {
        requireLayoutNotStarted();
        if (topMargin < 0 || rightMargin < 0 || bottomMargin < 0 || leftMargin < 0
            || topMargin + bottomMargin >= pageSize.getHeight()
            || leftMargin + rightMargin >= pageSize.getWidth()) {
            throw new IllegalArgumentException("Margins must leave a positive page content area");
        }
        this.topMargin = topMargin;
        this.rightMargin = rightMargin;
        this.bottomMargin = bottomMargin;
        this.leftMargin = leftMargin;
    }

    public PdfDocument getPdfDocument() {
        return pdfDocument;
    }

    public Document setFont(PdfFont value) {
        requireLayoutNotStarted();
        font = Objects.requireNonNull(value, "value");
        return this;
    }

    public Document setFontSize(float value) {
        requireLayoutNotStarted();
        if (!(value > 0) || !Float.isFinite(value)) {
            throw new IllegalArgumentException("Font size must be positive and finite");
        }
        fontSize = value;
        return this;
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        if (pending != null) {
            layout().render(pending, null);
            pending = null;
        } else if (pdfDocument.getNumberOfPages() == 0) {
            pdfDocument.addNewPage(pageSize);
        }
        pdfDocument.close();
    }

    private LayoutEngine layout() {
        if (layout == null) {
            layout = new LayoutEngine(
                pdfDocument, pageSize, topMargin, rightMargin, bottomMargin, leftMargin, font, fontSize
            );
        }
        return layout;
    }

    private void requireLayoutNotStarted() {
        if (layout != null) {
            throw new IllegalStateException("Document defaults cannot change after layout has started");
        }
    }
}
