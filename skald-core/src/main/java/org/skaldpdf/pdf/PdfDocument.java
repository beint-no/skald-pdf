package org.skaldpdf.pdf;

import org.jspecify.annotations.Nullable;
import org.skaldpdf.event.AbstractPdfDocumentEventHandler;
import org.skaldpdf.event.PdfDocumentEvent;
import org.skaldpdf.geom.PageSize;
import org.skaldpdf.image.ImageData;

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
    private final @Nullable PdfWriter writer;
    private final @Nullable PdfReader reader;
    private final PdfDocumentInfo documentInfo = new PdfDocumentInfo();
    private final Map<String, List<AbstractPdfDocumentEventHandler>> eventHandlers = new LinkedHashMap<>();
    private final List<PdfPage> pages = new ArrayList<>();
    private final List<OutlineItem> outlines = new ArrayList<>();
    private final Map<String, NamedDestination> namedDestinations = new LinkedHashMap<>();
    private String language = "";
    private @Nullable SignatureField signatureField;
    private final Map<ImportedImageKey, ImageData> importedImageReplacements = new LinkedHashMap<>();
    private boolean closed;
    private boolean closing;

    public PdfDocument(PdfWriter writer) {
        this.writer = Objects.requireNonNull(writer, "writer");
        reader = null;
    }

    public PdfDocument(PdfReader reader) {
        this(reader, null);
    }

    public PdfDocument(PdfReader reader, @Nullable PdfWriter writer) {
        this.reader = Objects.requireNonNull(reader, "reader");
        this.writer = writer;
        try {
            var bytes = reader.bytes();
            if (writer != null && NativePdfParser.containsSealedSignature(bytes)) {
                throw new IllegalStateException(
                    "Rewriting a sealed PDF would invalidate its signatures; use PdfSigner.sign to add another seal");
            }
            var parser = new NativePdfParser(bytes);
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

    public @Nullable SignatureField signatureField() {
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

    /**
     * Image XObjects on imported pages, in page order. Generated pages that
     * were never parsed contribute nothing; reopen the file first.
     */
    public List<EmbeddedImage> importedImages() {
        ensureOpen();
        var result = new ArrayList<EmbeddedImage>();
        for (int index = 0; index < pages.size(); index++) {
            var imported = pages.get(index).importedPage();
            if (imported != null) {
                result.addAll(imported.source().imageXObjects(imported, index + 1));
            }
        }
        return List.copyOf(result);
    }

    /**
     * Replaces one imported image XObject. The page content stream keeps the
     * same resource name, so the existing {@code Do} operator continues to
     * work. The replacement is written as a new DCT or Flate stream.
     */
    public PdfDocument replaceImportedImage(int pageNumber, String resourceName, ImageData image) {
        ensureOpen();
        Objects.requireNonNull(resourceName, "resourceName");
        Objects.requireNonNull(image, "image");
        var page = getPage(pageNumber);
        if (page.importedPage() == null) {
            throw new IllegalArgumentException("Only imported pages can replace image XObjects");
        }
        var present = importedImages().stream()
            .anyMatch(candidate -> candidate.pageNumber() == pageNumber
                && candidate.resourceName().equals(resourceName));
        if (!present) {
            throw new IllegalArgumentException("No imported image named " + resourceName + " on page " + pageNumber);
        }
        importedImageReplacements.put(new ImportedImageKey(pageNumber, resourceName), image);
        return this;
    }

    Map<ImportedImageKey, ImageData> importedImageReplacements() {
        return Map.copyOf(importedImageReplacements);
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
                    new NativePdfWriter(writer.properties()).write(this, writer.output());
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

    record ImportedImageKey(int pageNumber, String resourceName) {
        ImportedImageKey {
            Objects.requireNonNull(resourceName, "resourceName");
        }
    }
}
