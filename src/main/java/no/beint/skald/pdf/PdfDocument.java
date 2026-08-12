package no.beint.skald.pdf;

import no.beint.skald.event.AbstractPdfDocumentEventHandler;
import no.beint.skald.event.PdfDocumentEvent;
import no.beint.skald.geom.PageSize;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class PdfDocument implements AutoCloseable {
    private final PDDocument document;
    private final PdfWriter writer;
    private final PdfReader reader;
    private final PdfDocumentInfo documentInfo;
    private final Map<String, List<AbstractPdfDocumentEventHandler>> eventHandlers = new LinkedHashMap<>();
    private final Map<PDPage, PDPageContentStream> contentStreams = new LinkedHashMap<>();
    private boolean closed;

    public PdfDocument(PdfWriter writer) {
        this.document = new PDDocument();
        this.writer = Objects.requireNonNull(writer, "writer");
        this.reader = null;
        this.documentInfo = new PdfDocumentInfo(document.getDocumentInformation());
    }

    public PdfDocument(PdfReader reader) {
        this(reader, null);
    }

    public PdfDocument(PdfReader reader, PdfWriter writer) {
        this.reader = Objects.requireNonNull(reader, "reader");
        this.writer = writer;
        try {
            this.document = Loader.loadPDF(reader.bytes());
        } catch (IOException exception) {
            throw new IllegalArgumentException("Unable to parse PDF", exception);
        }
        this.documentInfo = new PdfDocumentInfo(document.getDocumentInformation());
    }

    public PdfDocumentInfo getDocumentInfo() {
        return documentInfo;
    }

    public int getNumberOfPages() {
        return document.getNumberOfPages();
    }

    public PdfPage getPage(int pageNumber) {
        if (pageNumber < 1 || pageNumber > getNumberOfPages()) {
            throw new IndexOutOfBoundsException("PDF page number is one-based: " + pageNumber);
        }
        return new PdfPage(this, document.getPage(pageNumber - 1));
    }

    public PdfPage addNewPage(PageSize pageSize) {
        var page = new PDPage(new PDRectangle(pageSize.getWidth(), pageSize.getHeight()));
        document.addPage(page);
        return new PdfPage(this, page);
    }

    public void addEventHandler(String type, AbstractPdfDocumentEventHandler handler) {
        eventHandlers.computeIfAbsent(type, ignored -> new ArrayList<>()).add(handler);
    }

    public PDDocument backingDocument() {
        return document;
    }

    public PDPageContentStream contentStream(PdfPage page) {
        return contentStreams.computeIfAbsent(page.backingPage(), targetPage -> {
            try {
                return new PDPageContentStream(document, targetPage, PDPageContentStream.AppendMode.APPEND, true, true);
            } catch (IOException exception) {
                throw new IllegalStateException("Unable to create PDF content stream", exception);
            }
        });
    }

    public boolean isClosed() {
        return closed;
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        try {
            flushContentStreams();
            var endPageHandlers = eventHandlers.getOrDefault(PdfDocumentEvent.END_PAGE, List.of());
            for (int pageNumber = 1; pageNumber <= getNumberOfPages(); pageNumber++) {
                var event = new PdfDocumentEvent(this, getPage(pageNumber));
                endPageHandlers.forEach(handler -> handler.accept(event));
            }
            flushContentStreams();
            if (writer != null) {
                document.save(writer.output());
                writer.close();
            }
            document.close();
            if (reader != null) {
                reader.close();
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to close PDF document", exception);
        }
    }

    private void flushContentStreams() throws IOException {
        IOException failure = null;
        for (var stream : contentStreams.values()) {
            try {
                stream.close();
            } catch (IOException exception) {
                if (failure == null) {
                    failure = exception;
                } else {
                    failure.addSuppressed(exception);
                }
            }
        }
        contentStreams.clear();
        if (failure != null) {
            throw failure;
        }
    }
}
