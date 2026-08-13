package org.skaldpdf.pdf;

/** Compression policies for newly written PDF streams. */
public enum Compression {
    NONE(0),
    FAST(1),
    BALANCED(6),
    MAXIMUM(9);

    private final int deflateLevel;

    Compression(int deflateLevel) {
        this.deflateLevel = deflateLevel;
    }

    int deflateLevel() {
        return deflateLevel;
    }
}
