package org.skaldpdf;

import org.skaldpdf.geom.PageSize;
import org.skaldpdf.layout.Document;
import org.skaldpdf.pdf.PdfDocument;
import org.skaldpdf.pdf.PdfEncryption;
import org.skaldpdf.pdf.PdfReader;
import org.skaldpdf.pdf.PdfText;
import org.skaldpdf.pdf.PdfWriter;
import org.skaldpdf.pdf.WriterProperties;
import org.skaldpdf.pdf.merge.PdfMerger;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/** Concise entry points for common generation and composition workflows. */
public final class Pdf {
    private Pdf() {
    }

    public static byte[] create(Consumer<Document> content) {
        return create(PageSize.A4, WriterProperties.defaults(), content);
    }

    public static byte[] create(PageSize pageSize, Consumer<Document> content) {
        return create(pageSize, WriterProperties.defaults(), content);
    }

    public static byte[] create(PageSize pageSize, WriterProperties properties, Consumer<Document> content) {
        var output = new ByteArrayOutputStream();
        write(output, pageSize, properties, content);
        return output.toByteArray();
    }

    public static void write(OutputStream output, PageSize pageSize, WriterProperties properties,
                             Consumer<Document> content) {
        Objects.requireNonNull(content, "content");
        try (var pdf = new PdfDocument(new PdfWriter(output, properties));
             var document = new Document(pdf, pageSize)) {
            content.accept(document);
        }
    }

    public static void write(Path path, Consumer<Document> content) {
        write(path, PageSize.A4, WriterProperties.defaults(), content);
    }

    public static void write(Path path, PageSize pageSize, WriterProperties properties,
                             Consumer<Document> content) {
        Objects.requireNonNull(content, "content");
        try (var pdf = new PdfDocument(new PdfWriter(path, properties));
             var document = new Document(pdf, pageSize)) {
            content.accept(document);
        }
    }

    public static byte[] rewrite(byte[] source, Consumer<PdfDocument> changes) {
        return rewrite(source, WriterProperties.defaults(), changes);
    }

    public static byte[] rewrite(byte[] source, WriterProperties properties, Consumer<PdfDocument> changes) {
        Objects.requireNonNull(changes, "changes");
        var output = new ByteArrayOutputStream();
        try (var document = new PdfDocument(new PdfReader(source), new PdfWriter(output, properties))) {
            changes.accept(document);
        }
        return output.toByteArray();
    }

    public static byte[] encrypt(byte[] source, PdfEncryption encryption) {
        return rewrite(source, WriterProperties.defaults().encrypted(encryption), document -> {
        });
    }

    public static void rewrite(Path source, Path target, Consumer<PdfDocument> changes) {
        Objects.requireNonNull(changes, "changes");
        try (var document = new PdfDocument(new PdfReader(source), new PdfWriter(target))) {
            changes.accept(document);
        }
    }

    public static String extractText(byte[] source) {
        return PdfText.extract(source);
    }

    public static byte[] merge(byte[]... sources) {
        Objects.requireNonNull(sources, "sources");
        return merge(List.of(sources));
    }

    public static byte[] merge(List<byte[]> sources) {
        Objects.requireNonNull(sources, "sources");
        var output = new ByteArrayOutputStream();
        try (var target = new PdfDocument(new PdfWriter(output))) {
            var merger = new PdfMerger(target);
            for (var sourceBytes : sources) {
                try (var source = new PdfDocument(new PdfReader(sourceBytes))) {
                    merger.merge(source, 1, source.getNumberOfPages());
                }
            }
        }
        return output.toByteArray();
    }

    public static void mergePaths(List<Path> sources, Path target) {
        Objects.requireNonNull(sources, "sources");
        try (var destination = new PdfDocument(new PdfWriter(target))) {
            var merger = new PdfMerger(destination);
            for (var sourcePath : sources) {
                try (var source = new PdfDocument(new PdfReader(sourcePath))) {
                    merger.merge(source, 1, source.getNumberOfPages());
                }
            }
        }
    }
}
