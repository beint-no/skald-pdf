package no.beint.skald.layout;

import no.beint.skald.geom.PageSize;
import no.beint.skald.font.PdfFont;
import no.beint.skald.font.PdfFontFactory;
import no.beint.skald.layout.element.LayoutElement;
import no.beint.skald.layout.internal.LayoutEngine;
import no.beint.skald.pdf.PdfDocument;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class Document implements AutoCloseable {
    private final PdfDocument pdfDocument;
    private final PageSize pageSize;
    private final List<LayoutElement> elements = new ArrayList<>();
    private float topMargin = 36f;
    private float rightMargin = 36f;
    private float bottomMargin = 36f;
    private float leftMargin = 36f;
    private PdfFont font = PdfFontFactory.regular();
    private float fontSize = 12f;
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
        elements.add(Objects.requireNonNull(element, "element"));
        return this;
    }

    public void setMargins(float topMargin, float rightMargin, float bottomMargin, float leftMargin) {
        this.topMargin = topMargin;
        this.rightMargin = rightMargin;
        this.bottomMargin = bottomMargin;
        this.leftMargin = leftMargin;
    }

    public PdfDocument getPdfDocument() {
        return pdfDocument;
    }

    public Document setFont(PdfFont value) {
        font = Objects.requireNonNull(value, "value");
        return this;
    }

    public Document setFontSize(float value) {
        fontSize = value;
        return this;
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        new LayoutEngine(pdfDocument, pageSize, topMargin, rightMargin, bottomMargin, leftMargin, font, fontSize)
            .render(elements);
        pdfDocument.close();
    }
}
