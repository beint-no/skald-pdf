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
    private final boolean experimentalJpegXl;

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
        this(output, properties, closeOutput, false);
    }

    private PdfWriter(OutputStream output, WriterProperties properties, boolean closeOutput, boolean experimentalJpegXl) {
        this.experimentalJpegXl = experimentalJpegXl;
        this.output = Objects.requireNonNull(output, "output");
        this.properties = Objects.requireNonNull(properties, "properties");
        this.closeOutput = closeOutput;
    }

    /**
     * Returns a writer over the same output with provisional /JXLDecode explicitly enabled.
     * Use the returned writer as the resource owner instead of closing both instances.
     * WriterProperties retains its existing two-component record contract.
     */
    public PdfWriter withExperimentalJpegXl() {
        return new PdfWriter(output, properties, closeOutput, true);
    }

    boolean experimentalJpegXl() { return experimentalJpegXl; }

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
