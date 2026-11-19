package org.skaldpdf.pdf;

import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

public final class PdfReader implements AutoCloseable {
    private static final int MAXIMUM_BYTES = 256 * 1024 * 1024;
    private final @Nullable InputStream input;
    private byte @Nullable [] cachedBytes;

    public PdfReader(InputStream input) {
        this.input = Objects.requireNonNull(input, "input");
    }

    public PdfReader(byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes");
        if (bytes.length > MAXIMUM_BYTES) {
            throw new IllegalArgumentException("PDF input exceeds the 256 MiB safety limit");
        }
        this.input = null;
        this.cachedBytes = bytes.clone();
    }

    public PdfReader(Path path) {
        this(open(path));
    }

    byte[] bytes() {
        if (cachedBytes != null) {
            return cachedBytes;
        }
        try {
            var result = Objects.requireNonNull(input).readNBytes(MAXIMUM_BYTES + 1);
            if (result.length > MAXIMUM_BYTES) {
                throw new IllegalArgumentException("PDF input exceeds the 256 MiB safety limit");
            }
            cachedBytes = result;
            return result;
        } catch (IOException exception) {
            throw new IllegalArgumentException("Unable to read PDF", exception);
        }
    }

    @Override
    public void close() {
        if (input == null) {
            return;
        }
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
