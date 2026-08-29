package org.skaldpdf.pdf;

import org.jspecify.annotations.Nullable;
import org.skaldpdf.event.AbstractPdfDocumentEventHandler;
import org.skaldpdf.event.PdfDocumentEvent;
import org.skaldpdf.geom.PageSize;
import org.skaldpdf.image.ImageData;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

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
    private final IdentityHashMap<CosValue.CosStream, ImageData> importedImageReplacements = new IdentityHashMap<>();
    private final Map<ImportedImageKey, ImageData> importedPageImageReplacements = new LinkedHashMap<>();
    private String language = "";
    private @Nullable SignatureField signatureField;
    private @Nullable NativePdfParser sourceParser;
    private boolean compressImportedStreamsLosslessly;
    private boolean deduplicateImportedImagesLosslessly;
    private boolean deduplicateImportedFontProgramsLosslessly;
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
            sourceParser = new NativePdfParser(bytes);
            sourceParser.pages().forEach(page -> pages.add(new PdfPage(this, page)));
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
     * Distinct image XObjects reachable from imported pages and Forms, in
     * first-use order. A stream shared by several resources is returned once.
     * Generated pages that were never parsed contribute nothing; reopen the file first.
     */
    public List<EmbeddedImage> importedImages() {
        ensureOpen();
        var result = new ArrayList<EmbeddedImage>();
        var streams = java.util.Collections.newSetFromMap(
            new IdentityHashMap<CosValue.CosStream, Boolean>());
        for (int index = 0; index < pages.size(); index++) {
            var imported = pages.get(index).importedPage();
            if (imported != null) {
                for (var image : imported.source().imageXObjects(imported, index + 1)) {
                    if (streams.add(image.stream())) {
                        result.add(image);
                    }
                }
            }
        }
        return List.copyOf(result);
    }

    /**
     * Whether an untouched imported document may be normalized without removing
     * linearization, incremental history, signatures, or declared conformance.
     */
    public boolean isSafeForCanonicalOptimization() {
        ensureOpen();
        return sourceParser != null && sourceParser.isSafeForCanonicalOptimization();
    }

    /**
     * Returns the source properties that make a canonical rewrite unsafe.
     * An empty set means the document is eligible for verified optimization.
     */
    public Set<CanonicalRewriteConstraint> canonicalRewriteConstraints() {
        ensureOpen();
        return sourceParser == null ? Set.of() : sourceParser.canonicalRewriteConstraints();
    }

    /**
     * Requests exact re-encoding of eligible imported streams during a
     * canonical rewrite. Stream dictionaries and decoded bytes are preserved;
     * the writer keeps the original representation unless Deflate is smaller.
     */
    public PdfDocument compressImportedStreamsLosslessly() {
        ensureOpen();
        compressImportedStreamsLosslessly = true;
        return this;
    }

    /**
     * Requests exact sharing of byte-identical, semantically simple image
     * XObjects during a canonical rewrite.
     */
    public PdfDocument deduplicateImportedImagesLosslessly() {
        ensureOpen();
        deduplicateImportedImagesLosslessly = true;
        return this;
    }

    /**
     * Requests exact sharing of byte-identical embedded font program streams
     * during a canonical rewrite. Font dictionaries, encodings, and glyphs
     * are not modified or interpreted.
     */
    public PdfDocument deduplicateImportedFontProgramsLosslessly() {
        ensureOpen();
        deduplicateImportedFontProgramsLosslessly = true;
        return this;
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
        var embedded = page.importedPage().source().imageXObjects(page.importedPage(), pageNumber).stream()
            .filter(candidate -> candidate.resourceName().equals(resourceName))
            .findFirst().orElse(null);
        if (embedded == null) {
            throw new IllegalArgumentException("No imported image named " + resourceName + " on page " + pageNumber);
        }
        replaceImportedImage(embedded, image);
        importedPageImageReplacements.put(new ImportedImageKey(pageNumber, resourceName), image);
        return this;
    }

    /** Replaces the exact imported image object, including images reached through Form XObjects. */
    public PdfDocument replaceImportedImage(EmbeddedImage embedded, ImageData image) {
        ensureOpen();
        Objects.requireNonNull(embedded, "embedded");
        Objects.requireNonNull(image, "image");
        if (sourceParser == null || embedded.parser() != sourceParser) {
            throw new IllegalArgumentException("The image does not belong to this PDF document");
        }
        importedImageReplacements.put(embedded.stream(), image);
        return this;
    }

    IdentityHashMap<CosValue.CosStream, ImageData> importedImageReplacements() {
        return new IdentityHashMap<>(importedImageReplacements);
    }

    Map<ImportedImageKey, ImageData> importedPageImageReplacements() {
        return Map.copyOf(importedPageImageReplacements);
    }

    boolean shouldCompressImportedStreamsLosslessly() {
        return compressImportedStreamsLosslessly;
    }

    boolean shouldDeduplicateImportedImagesLosslessly() {
        return deduplicateImportedImagesLosslessly;
    }

    boolean shouldDeduplicateImportedFontProgramsLosslessly() {
        return deduplicateImportedFontProgramsLosslessly;
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

    @Nullable NativePdfParser canonicalRewriteSource() {
        if (sourceParser == null || !documentInfo.isEmpty() || !language.isEmpty()
            || !outlines.isEmpty() || !namedDestinations.isEmpty() || signatureField != null
            || pages.size() != sourceParser.pages().size()) {
            return null;
        }
        for (int index = 0; index < pages.size(); index++) {
            var page = pages.get(index);
            if (!page.isUnmodifiedImportedPage() || page.importedPage() != sourceParser.pages().get(index)) {
                return null;
            }
        }
        return sourceParser;
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
