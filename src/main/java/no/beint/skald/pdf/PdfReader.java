package no.beint.skald.pdf;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

public final class PdfReader implements AutoCloseable {
    private final InputStream input;

    public PdfReader(InputStream input) {
        this.input = Objects.requireNonNull(input, "input");
    }

    byte[] bytes() {
        try {
            return input.readAllBytes();
        } catch (IOException exception) {
            throw new IllegalArgumentException("Unable to read PDF", exception);
        }
    }

    @Override
    public void close() {
        try {
            input.close();
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to close PDF input", exception);
        }
    }
}
