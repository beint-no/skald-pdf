package no.beint.skald.pdf;

public final class WriterProperties {
    private int compressionLevel = 6;

    public WriterProperties setCompressionLevel(int value) {
        if (value < 0 || value > 9) {
            throw new IllegalArgumentException("Compression level must be between 0 and 9");
        }
        compressionLevel = value;
        return this;
    }

    public int compressionLevel() {
        return compressionLevel;
    }
}
