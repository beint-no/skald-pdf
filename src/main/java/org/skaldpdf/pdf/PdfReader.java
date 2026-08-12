package org.skaldpdf.pdf;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

public final class PdfReader implements AutoCloseable {
    private static final int MAXIMUM_BYTES = 256 * 1024 * 1024;
    private final InputStream input;

    public PdfReader(InputStream input) {
        this.input = Objects.requireNonNull(input, "input");
    }

    public PdfReader(byte[] bytes) {
        this(new ByteArrayInputStream(Objects.requireNonNull(bytes, "bytes")));
    }

    public PdfReader(Path path) {
        this(open(path));
    }

    byte[] bytes() {
        try {
            var result = input.readNBytes(MAXIMUM_BYTES + 1);
            if (result.length > MAXIMUM_BYTES) {
                throw new IllegalArgumentException("PDF input exceeds the 256 MiB safety limit");
            }
            return result;
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

    private static InputStream open(Path path) {
        try {
            return Files.newInputStream(Objects.requireNonNull(path, "path"));
        } catch (IOException exception) {
            throw new IllegalArgumentException("Unable to open PDF input: " + path, exception);
        }
    }
}
