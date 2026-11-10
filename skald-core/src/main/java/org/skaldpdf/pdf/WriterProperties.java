package org.skaldpdf.pdf;

import org.jspecify.annotations.Nullable;

import java.util.Objects;

/** Immutable options for a PDF writer. */
public record WriterProperties(Compression compression, @Nullable PdfEncryption encryption, boolean experimentalJpegXl) {
    public WriterProperties {
        Objects.requireNonNull(compression, "compression");
    }

    /** Compatibility constructor; experimental JPEG XL output is disabled. */
    public WriterProperties(Compression compression, @Nullable PdfEncryption encryption) {
        this(compression, encryption, false);
    }

    /** Explicitly permits provisional /JXLDecode output for interop experiments. */
    public WriterProperties withExperimentalJpegXl() {
        return new WriterProperties(compression, encryption, true);
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
        return new WriterProperties(compression, Objects.requireNonNull(value, "value"), experimentalJpegXl);
    }
}
