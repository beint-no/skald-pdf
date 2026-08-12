package org.skaldpdf.pdf;

import java.util.Objects;

/** Immutable options for a PDF writer. */
public record WriterProperties(Compression compression) {
    public WriterProperties {
        Objects.requireNonNull(compression, "compression");
    }

    public WriterProperties() {
        this(Compression.BALANCED);
    }

    public static WriterProperties defaults() {
        return new WriterProperties();
    }
}
