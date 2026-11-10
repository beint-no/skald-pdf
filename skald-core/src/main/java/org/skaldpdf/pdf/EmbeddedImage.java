package org.skaldpdf.pdf;

import org.jspecify.annotations.Nullable;
import org.skaldpdf.image.ImageData;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;

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
    private final boolean requiresOriginalDimensions;
    private final boolean softMaskResampled;
    private final NativePdfParser parser;
    private final CosValue.CosStream stream;
    private @Nullable ImageData decoded;
    private boolean decodeAttempted;

    EmbeddedImage(int pageNumber, String resourceName, int width, int height, String filter,
                  String colorSpace, int bitsPerComponent, boolean jpeg, boolean safeToRecompress,
                  boolean requiresOriginalDimensions,
                  NativePdfParser parser, CosValue.CosStream stream) {
        this(pageNumber, resourceName, width, height, filter, colorSpace, bitsPerComponent, jpeg, safeToRecompress,
            requiresOriginalDimensions, false, parser, stream);
    }

    private EmbeddedImage(int pageNumber, String resourceName, int width, int height, String filter,
                          String colorSpace, int bitsPerComponent, boolean jpeg, boolean safeToRecompress,
                          boolean requiresOriginalDimensions, boolean softMaskResampled,
                          NativePdfParser parser, CosValue.CosStream stream) {
        this.softMaskResampled = softMaskResampled;
        this.pageNumber = pageNumber;
        this.resourceName = Objects.requireNonNull(resourceName, "resourceName");
        this.width = width;
        this.height = height;
        this.filter = Objects.requireNonNull(filter, "filter");
        this.colorSpace = Objects.requireNonNull(colorSpace, "colorSpace");
        this.bitsPerComponent = bitsPerComponent;
        this.jpeg = jpeg;
        this.safeToRecompress = safeToRecompress;
        this.requiresOriginalDimensions = requiresOriginalDimensions;
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

    /** Whether this is a provisional /JXLDecode stream. */
    public boolean jpegXl() {
        return filter.endsWith("JXLDecode");
    }

    public boolean jpeg() {
        return jpeg;
    }

    /** Whether replacing this stream can preserve its PDF image semantics. */
    public boolean safeToRecompress() {
        return safeToRecompress;
    }

    /**
     * Whether this image has a preserved soft mask and therefore must not be
     * resampled independently from that mask.
     */
    public boolean requiresOriginalDimensions() {
        return requiresOriginalDimensions;
    }

    public int encodedLength() {
        return stream.encodedLength();
    }

    public byte[] encodedBytes() {
        return stream.encodedBytes();
    }

    /**
     * Decodes DCT or 8-bit DeviceRGB, DeviceGray, or three-component ICCBased
     * Flate streams (and Brotli streams when an optional provider is available) into
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

    /**
     * The lowest resolution, in pixels per inch, at which imported page content
     * paints this image, taken from its largest placement on any page. Empty when
     * any use cannot be followed (annotation appearances, patterns, soft-mask
     * groups, Type3 glyphs, content that does not parse cleanly) or the image is
     * never painted, so callers can treat empty as "keep the resolution".
     */
    public OptionalDouble placedPixelsPerInch() {
        try {
            return parser.imagePlacements().pixelsPerInch(stream);
        } catch (RuntimeException unfollowable) {
            return OptionalDouble.empty();
        }
    }

    /**
     * This image with permission to change its size because its soft mask will be
     * resampled with it, or empty when the image has no soft mask of its own.
     * Replace it with {@link PdfDocument#replaceImportedImage(EmbeddedImage, ImageData, ImageData)}
     * when the size changes.
     */
    public Optional<EmbeddedImage> withResampledSoftMask() {
        if (!safeToRecompress || !requiresOriginalDimensions || !parser.softMaskResamplable(stream)) {
            return Optional.empty();
        }
        return Optional.of(new EmbeddedImage(pageNumber, resourceName, width, height, filter, colorSpace,
            bitsPerComponent, jpeg, true, false, true, parser, stream));
    }

    /** The soft mask as 8-bit gray samples at its own size, for an image from {@link #withResampledSoftMask()}. */
    public Optional<ImageData> decodeSoftMask() {
        if (!softMaskResampled) {
            return Optional.empty();
        }
        try {
            return Optional.of(parser.decodeSoftMask(stream));
        } catch (RuntimeException unsupported) {
            return Optional.empty();
        }
    }

    boolean softMaskResampled() {
        return softMaskResampled;
    }

    NativePdfParser parser() {
        return parser;
    }

    CosValue.CosStream stream() {
        return stream;
    }
}
