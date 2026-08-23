package org.skaldpdf.pdf;

import org.jspecify.annotations.Nullable;

import java.util.Objects;

/** Immutable options for a PDF writer. */
public record WriterProperties(Compression compression, @Nullable PdfEncryption encryption) {
    public WriterProperties {
        Objects.requireNonNull(compression, "compression");
    }

    public WriterProperties() {
        this(Compression.BALANCED, null);
    }

    public WriterProperties(Compression compression) {
        this(compression, null);
    }

    public static WriterProperties defaults() {
        return new WriterProperties();
    }

    public WriterProperties encrypted(PdfEncryption value) {
        return new WriterProperties(compression, Objects.requireNonNull(value, "value"));
    }
}
