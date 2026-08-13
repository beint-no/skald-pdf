package org.skaldpdf.layout;

import org.skaldpdf.geom.PageSize;
import org.skaldpdf.font.PdfFont;
import org.skaldpdf.font.PdfFontFactory;
import org.skaldpdf.layout.element.LayoutElement;
import org.skaldpdf.layout.internal.LayoutEngine;
import org.skaldpdf.pdf.PdfDocument;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

public final class Document implements AutoCloseable {
    private final PdfDocument pdfDocument;
    private final PageSize pageSize;
    private float topMargin = 36f;
    private float rightMargin = 36f;
    private float bottomMargin = 36f;
    private float leftMargin = 36f;
    private PdfFont font = PdfFontFactory.regular();
    private final List<PdfFont> fallbacks = new ArrayList<>();
    private boolean fallbacksExplicit;
    private float fontSize = 12f;
    private float headerHeight;
    private float firstHeaderHeight;
    private float footerHeight;
    private Function<PageNumbering, LayoutElement> header;
    private Function<PageNumbering, LayoutElement> firstHeader;
    private Function<PageNumbering, LayoutElement> footer;
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

    public Document setMargins(float topMargin, float rightMargin, float bottomMargin, float leftMargin) {
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
        return this;
    }

    public PdfDocument getPdfDocument() {
        return pdfDocument;
    }

    public Document setFont(PdfFont value) {
        requireLayoutNotStarted();
        font = Objects.requireNonNull(value, "value");
        if (!fallbacksExplicit && fallbacks.isEmpty() && !PdfFontFactory.bundled(font)) {
            fallbacks.add(PdfFontFactory.regular());
        }
        return this;
    }

    public Document setFontFallbacks(PdfFont... fonts) {
        requireLayoutNotStarted();
        fallbacksExplicit = true;
        fallbacks.clear();
        for (var fallback : fonts) {
            fallbacks.add(Objects.requireNonNull(fallback, "fallback"));
        }
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

    public Document setTitle(String value) {
        pdfDocument.getDocumentInfo().setTitle(value);
        return this;
    }

    public Document setAuthor(String value) {
        pdfDocument.getDocumentInfo().setAuthor(value);
        return this;
    }

    public Document setSubject(String value) {
        pdfDocument.getDocumentInfo().setSubject(value);
        return this;
    }

    public Document setKeywords(String value) {
        pdfDocument.getDocumentInfo().setKeywords(value);
        return this;
    }

    public Document setLanguage(String value) {
        pdfDocument.setLanguage(value);
        return this;
    }

    public Document addOutline(String title, int pageNumber) {
        pdfDocument.addOutline(title, pageNumber);
        return this;
    }

    public Document setHeader(float height, Function<PageNumbering, LayoutElement> content) {
        requireLayoutNotStarted();
        headerHeight = nonNegativeBand(height, "Header height");
        header = content;
        return this;
    }

    public Document setFooter(float height, Function<PageNumbering, LayoutElement> content) {
        requireLayoutNotStarted();
        footerHeight = nonNegativeBand(height, "Footer height");
        footer = content;
        return this;
    }

    public Document setFirstHeader(Function<PageNumbering, LayoutElement> content) {
        requireLayoutNotStarted();
        firstHeader = content;
        return this;
    }

    public Document setFirstHeader(float height, Function<PageNumbering, LayoutElement> content) {
        requireLayoutNotStarted();
        firstHeaderHeight = nonNegativeBand(height, "First header height");
        firstHeader = content;
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
        if (header != null || firstHeader != null || footer != null) {
            layout().finishPages(header, firstHeader, footer);
        }
        pdfDocument.close();
    }

    private LayoutEngine layout() {
        if (layout == null) {
            if (topMargin + Math.max(headerHeight, firstHeaderHeight) + bottomMargin + footerHeight
                >= pageSize.getHeight()
                || leftMargin + rightMargin >= pageSize.getWidth()) {
                throw new IllegalArgumentException("Margins, header, and footer must leave a positive page content area");
            }
            layout = new LayoutEngine(
                pdfDocument, pageSize, topMargin, rightMargin, bottomMargin, leftMargin, font, fontSize,
                headerHeight, footerHeight, firstHeaderHeight, List.copyOf(fallbacks)
            );
        }
        return layout;
    }

    private static float nonNegativeBand(float value, String name) {
        if (value < 0 || !Float.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be non-negative and finite");
        }
        return value;
    }

    private void requireLayoutNotStarted() {
        if (layout != null) {
            throw new IllegalStateException("Document defaults cannot change after layout has started");
        }
    }
}
