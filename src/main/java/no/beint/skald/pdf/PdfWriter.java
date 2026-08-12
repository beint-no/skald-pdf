package no.beint.skald.pdf;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Objects;

public final class PdfWriter implements AutoCloseable {
    private final OutputStream output;
    private final WriterProperties properties;

    public PdfWriter(OutputStream output) {
        this(output, new WriterProperties());
    }

    public PdfWriter(OutputStream output, WriterProperties properties) {
        this.output = Objects.requireNonNull(output, "output");
        this.properties = Objects.requireNonNull(properties, "properties");
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
            output.flush();
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to flush PDF output", exception);
        }
    }
}
