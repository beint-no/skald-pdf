package org.skaldpdf.pdf;

import org.skaldpdf.event.AbstractPdfDocumentEventHandler;
import org.skaldpdf.event.PdfDocumentEvent;
import org.skaldpdf.geom.PageSize;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Mutable PDF document for generation, composition, and read-modify-write
 * stamping. A document is thread-confined; separate documents may safely be
 * generated on virtual threads.
 */
public final class PdfDocument implements AutoCloseable {
    private final PdfWriter writer;
    private final PdfReader reader;
    private final PdfDocumentInfo documentInfo = new PdfDocumentInfo();
    private final Map<String, List<AbstractPdfDocumentEventHandler>> eventHandlers = new LinkedHashMap<>();
    private final List<PdfPage> pages = new ArrayList<>();
    private final List<OutlineItem> outlines = new ArrayList<>();
    private final Map<String, NamedDestination> namedDestinations = new LinkedHashMap<>();
    private String language;
    private SignatureField signatureField;
    private boolean closed;
    private boolean closing;

    public PdfDocument(PdfWriter writer) {
        this.writer = Objects.requireNonNull(writer, "writer");
        reader = null;
    }

    public PdfDocument(PdfReader reader) {
        this(reader, null);
    }

    public PdfDocument(PdfReader reader, PdfWriter writer) {
        this.reader = Objects.requireNonNull(reader, "reader");
        this.writer = writer;
        try {
            var parser = new NativePdfParser(reader.bytes());
            parser.pages().forEach(page -> pages.add(new PdfPage(this, page)));
        } catch (RuntimeException exception) {
            reader.close();
            throw exception;
        }
    }

    public PdfDocumentInfo getDocumentInfo() {
        return documentInfo;
    }

    public PdfDocument setLanguage(String value) {
        ensureOpen();
        if (value == null || value.isBlank() || value.codePoints().anyMatch(code -> code < 0x20 || code > 0x7e)) {
            throw new IllegalArgumentException("Document language must be a printable BCP 47 tag");
        }
        language = value.strip();
        return this;
    }

    public String language() {
        return language;
    }

    public PdfDocument addOutline(String title, int pageNumber) {
        ensureOpen();
        outlines.add(new OutlineItem(title, pageNumber));
        return this;
    }

    public List<OutlineItem> outlines() {
        return List.copyOf(outlines);
    }

    public PdfDocument addNamedDestination(String name, int pageNumber, float top) {
        ensureOpen();
        var dest = new NamedDestination(name, pageNumber, top);
        var previous = namedDestinations.putIfAbsent(dest.name(), dest);
        if (previous != null) {
            throw new IllegalArgumentException("Named destination already exists: " + dest.name());
        }
        return this;
    }

    public List<NamedDestination> namedDestinations() {
        return List.copyOf(namedDestinations.values());
    }

    public PdfDocument prepareSignature(SignatureField field) {
        ensureOpen();
        if (signatureField != null) {
            throw new IllegalStateException("A document can reserve only one signature field");
        }
        signatureField = Objects.requireNonNull(field, "field");
        return this;
    }

    public SignatureField signatureField() {
        return signatureField;
    }

    public int getNumberOfPages() {
        return pages.size();
    }

    public PdfPage getPage(int pageNumber) {
        if (pageNumber < 1 || pageNumber > pages.size()) {
            throw new IndexOutOfBoundsException("PDF page number is one-based: " + pageNumber);
        }
        return pages.get(pageNumber - 1);
    }

    public PdfPage addNewPage(PageSize pageSize) {
        ensureOpen();
        var page = new PdfPage(this, Objects.requireNonNull(pageSize, "pageSize"));
        pages.add(page);
        return page;
    }

    public void addEventHandler(String type, AbstractPdfDocumentEventHandler handler) {
        ensureOpen();
        eventHandlers.computeIfAbsent(Objects.requireNonNull(type, "type"), ignored -> new ArrayList<>())
            .add(Objects.requireNonNull(handler, "handler"));
    }

    public void copyPagesFrom(PdfDocument source, int fromPage, int toPage) {
        ensureOpen();
        Objects.requireNonNull(source, "source");
        if (fromPage < 1 || toPage < fromPage || toPage > source.getNumberOfPages()) {
            throw new IllegalArgumentException("Invalid page range");
        }
        for (int pageNumber = fromPage; pageNumber <= toPage; pageNumber++) {
            var imported = source.getPage(pageNumber).importedPage();
            if (imported == null) {
                throw new IllegalArgumentException(
                    "Only parsed pages can be copied; close and reopen generated documents before merging");
            }
            pages.add(new PdfPage(this, imported));
        }
    }

    public boolean isClosed() {
        return closed;
    }

    @Override
    public void close() {
        if (closed || closing) {
            return;
        }
        closing = true;
        try {
            var endPageHandlers = eventHandlers.getOrDefault(PdfDocumentEvent.END_PAGE, List.of());
            for (var page : pages) {
                var event = new PdfDocumentEvent(this, page);
                endPageHandlers.forEach(handler -> handler.accept(event));
            }
            if (writer != null) {
                try (writer) {
                    new NativePdfWriter(writer.properties().compression().deflateLevel()).write(this, writer.output());
                }
            }
        } finally {
            if (reader != null) {
                reader.close();
            }
            closed = true;
            closing = false;
        }
    }

    List<PdfPage> pages() {
        return List.copyOf(pages);
    }

    public void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("PDF document is closed");
        }
    }
}
