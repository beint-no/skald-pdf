package org.skaldpdf.pdf;

import org.jspecify.annotations.Nullable;
import org.skaldpdf.image.ImageData;

import java.util.Objects;
import java.util.Optional;

/**
 * An {@code /XObject /Image} found on an imported page. The encoded stream is
 * left untouched until {@link #decode()} is called. Unsupported filters
 * (JPEG 2000, JBIG2, CCITT) yield an empty decode result rather than failing
 * the surrounding document.
 */
public final class EmbeddedImage {
    private final int pageNumber;
    private final String resourceName;
    private final int width;
    private final int height;
    private final String filter;
    private final String colorSpace;
    private final int bitsPerComponent;
    private final boolean jpeg;
    private final boolean safeToRecompress;
    private final byte[] encodedBytes;
    private final NativePdfParser parser;
    private final CosValue.CosStream stream;
    private @Nullable ImageData decoded;
    private boolean decodeAttempted;

    EmbeddedImage(int pageNumber, String resourceName, int width, int height, String filter,
                  String colorSpace, int bitsPerComponent, boolean jpeg, boolean safeToRecompress, byte[] encodedBytes,
                  NativePdfParser parser, CosValue.CosStream stream) {
        this.pageNumber = pageNumber;
        this.resourceName = Objects.requireNonNull(resourceName, "resourceName");
        this.width = width;
        this.height = height;
        this.filter = Objects.requireNonNull(filter, "filter");
        this.colorSpace = Objects.requireNonNull(colorSpace, "colorSpace");
        this.bitsPerComponent = bitsPerComponent;
        this.jpeg = jpeg;
        this.safeToRecompress = safeToRecompress;
        this.encodedBytes = Objects.requireNonNull(encodedBytes, "encodedBytes");
        this.parser = parser;
        this.stream = stream;
    }

    public int pageNumber() {
        return pageNumber;
    }

    public String resourceName() {
        return resourceName;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public String filter() {
        return filter;
    }

    public String colorSpace() {
        return colorSpace;
    }

    public int bitsPerComponent() {
        return bitsPerComponent;
    }

    public boolean jpeg() {
        return jpeg;
    }

    /** Whether replacing this stream can preserve its PDF image semantics. */
    public boolean safeToRecompress() {
        return safeToRecompress;
    }

    public int encodedLength() {
        return encodedBytes.length;
    }

    public byte[] encodedBytes() {
        return encodedBytes.clone();
    }

    /**
     * Decodes DCT or 8-bit DeviceRGB/DeviceGray Flate streams into
     * {@link ImageData}. Empty when the filter or colour space is outside the
     * supported subset.
     */
    public Optional<ImageData> decode() {
        if (!decodeAttempted) {
            decodeAttempted = true;
            try {
                decoded = parser.decodeImage(stream);
            } catch (RuntimeException ignored) {
                decoded = null;
            }
        }
        return Optional.ofNullable(decoded);
    }

    NativePdfParser parser() {
        return parser;
    }

    CosValue.CosStream stream() {
        return stream;
    }
}
