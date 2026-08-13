package org.skaldpdf.pdf;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

public final class PdfWriter implements AutoCloseable {
    private final OutputStream output;
    private final WriterProperties properties;
    private final boolean closeOutput;

    public PdfWriter(OutputStream output) {
        this(output, new WriterProperties(), false);
    }

    public PdfWriter(OutputStream output, WriterProperties properties) {
        this(output, properties, false);
    }

    public PdfWriter(Path path) {
        this(path, WriterProperties.defaults());
    }

    public PdfWriter(Path path, WriterProperties properties) {
        this(open(path), properties, true);
    }

    private PdfWriter(OutputStream output, WriterProperties properties, boolean closeOutput) {
        this.output = Objects.requireNonNull(output, "output");
        this.properties = Objects.requireNonNull(properties, "properties");
        this.closeOutput = closeOutput;
    }

    OutputStream output() {
        return output;
    }

    WriterProperties properties() {
        return properties;
    }

    @Override
    public void close() {
        try {
            if (closeOutput) {
                output.close();
            } else {
                output.flush();
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to close PDF output", exception);
        }
    }

    private static OutputStream open(Path path) {
        try {
            return Files.newOutputStream(Objects.requireNonNull(path, "path"));
        } catch (IOException exception) {
            throw new IllegalArgumentException("Unable to open PDF output: " + path, exception);
        }
    }
}
