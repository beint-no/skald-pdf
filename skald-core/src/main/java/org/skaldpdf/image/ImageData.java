package org.skaldpdf.image;

import org.jspecify.annotations.Nullable;
import org.skaldpdf.pdf.PdfDocument;
import org.skaldpdf.pdf.PdfPage;

import java.util.Locale;
import java.util.Objects;

/** Immutable raster image prepared for direct PDF embedding. */
public final class ImageData implements ImageSource {
    private static final long MAXIMUM_PIXELS = 100_000_000L;
    private static final int MAXIMUM_ENCODED_BYTES = 32 * 1024 * 1024;

    private final byte[] samples;
    private final byte @Nullable [] alpha;
    private final int width;
    private final int height;
    private final int components;
    private final boolean jpeg;

    /** Creates a pass-through DCT image after validating its header and safe dimensions. */
    public static ImageData fromJpeg(byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes");
        if (bytes.length == 0) {
            throw new IllegalArgumentException("Image data is empty");
        }
        if (bytes.length > MAXIMUM_ENCODED_BYTES) {
            throw new IllegalArgumentException("Image data exceeds the safe encoded-size limit");
        }
        var header = jpegHeader(bytes);
        if (header.width <= 0 || header.height <= 0
            || (long) header.width * header.height > MAXIMUM_PIXELS) {
            throw new IllegalArgumentException("Image dimensions exceed the safe decoding limit");
        }
        if (header.components != 1 && header.components != 3) {
            throw new IllegalArgumentException("JPEG must use DeviceGray or DeviceRGB samples");
        }
        return new ImageData(bytes.clone(), null, header.width, header.height, header.components, true);
    }

    private static ImageHeader jpegHeader(byte[] bytes) {
        if (bytes.length < 3 || (bytes[0] & 0xff) != 0xff || (bytes[1] & 0xff) != 0xd8) {
            throw new IllegalArgumentException("Unsupported or invalid JPEG data");
        }
        var index = 2;
        while (index + 8 < bytes.length) {
            if ((bytes[index] & 0xff) != 0xff) {
                throw new IllegalArgumentException("Truncated JPEG");
            }
            while (index < bytes.length && (bytes[index] & 0xff) == 0xff) {
                index++;
            }
            if (index >= bytes.length) {
                break;
            }
            var marker = bytes[index++] & 0xff;
            if (marker == 0xd9 || marker == 0xda) {
                break;
            }
            if (marker == 0x01 || (marker >= 0xd0 && marker <= 0xd8)) {
                continue;
            }
            if (index + 1 >= bytes.length) {
                break;
            }
            var length = ((bytes[index] & 0xff) << 8) | (bytes[index + 1] & 0xff);
            if (length < 2 || index + length > bytes.length) {
                throw new IllegalArgumentException("Truncated JPEG");
            }
            if (marker >= 0xc0 && marker <= 0xcf && marker != 0xc4 && marker != 0xc8 && marker != 0xcc) {
                if (length < 8) {
                    throw new IllegalArgumentException("Truncated JPEG");
                }
                var height = ((bytes[index + 3] & 0xff) << 8) | (bytes[index + 4] & 0xff);
                var width = ((bytes[index + 5] & 0xff) << 8) | (bytes[index + 6] & 0xff);
                return new ImageHeader(width, height, bytes[index + 7] & 0xff);
            }
            index += length;
        }
        throw new IllegalArgumentException("JPEG has no frame header");
    }

    private record ImageHeader(int width, int height, int components) {
    }

    private static boolean isOpaque(byte[] alpha) {
        for (var value : alpha) {
            if ((value & 0xff) != 0xff) {
                return false;
            }
        }
        return true;
    }

    /**
     * Packed 8-bit DeviceRGB samples, top-down, three bytes per pixel.
     * Use this when a native codec has already decoded a photo.
     */
    public static ImageData fromRgb(int width, int height, byte[] rgb) {
        return fromRaster(width, height, 3, rgb);
    }

    /** Creates DeviceRGB samples with a separate 8-bit alpha plane. */
    public static ImageData fromRgb(int width, int height, byte[] rgb, byte[] alpha) {
        Objects.requireNonNull(alpha, "alpha");
        if (alpha.length != Math.multiplyExact(width, height)) {
            throw new IllegalArgumentException("Alpha sample length does not match its dimensions");
        }
        var image = fromRaster(width, height, 3, rgb);
        return new ImageData(image.samples, isOpaque(alpha) ? null : alpha.clone(), width, height, 3, false);
    }

    /**
     * Packed 8-bit DeviceGray samples, top-down, one byte per pixel.
     */
    public static ImageData fromGray(int width, int height, byte[] gray) {
        return fromRaster(width, height, 1, gray);
    }

    private static ImageData fromRaster(int width, int height, int components, byte[] samples) {
        Objects.requireNonNull(samples, "samples");
        if (width < 1 || height < 1) {
            throw new IllegalArgumentException("Image dimensions must be positive");
        }
        if ((long) width * height > MAXIMUM_PIXELS) {
            throw new IllegalArgumentException("Image dimensions exceed the safe decoding limit");
        }
        var expected = Math.multiplyExact(Math.multiplyExact(width, height), components);
        if (samples.length != expected) {
            throw new IllegalArgumentException("Raster sample length does not match its dimensions");
        }
        return new ImageData(samples.clone(), null, width, height, components, false);
    }

    private ImageData(byte[] samples, byte @Nullable [] alpha, int width, int height, int components, boolean jpeg) {
        this.samples = samples;
        this.alpha = alpha;
        this.width = width;
        this.height = height;
        this.components = components;
        this.jpeg = jpeg;
    }

    public byte[] samples() {
        return samples.clone();
    }

    public byte @Nullable [] alpha() {
        return alpha == null ? null : alpha.clone();
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public int components() {
        return components;
    }

    public boolean jpeg() {
        return jpeg;
    }

    @Override
    public float intrinsicWidth() {
        return width;
    }

    @Override
    public float intrinsicHeight() {
        return height;
    }

    @Override
    public void drawOn(PdfDocument document, PdfPage page, float x, float y, float width, float height) {
        Objects.requireNonNull(document, "document").ensureOpen();
        Objects.requireNonNull(page, "page");
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Image dimensions must be positive");
        }
        var imageName = page.registerImage(this);
        page.append(new StringBuilder("q\n")
            .append(number(width)).append(" 0 0 ").append(number(height)).append(' ')
            .append(number(x)).append(' ').append(number(y)).append(" cm\n/")
            .append(imageName).append(" Do\nQ\n").toString());
    }

    private static String number(float value) {
        if (!Float.isFinite(value)) {
            throw new IllegalArgumentException("PDF number must be finite");
        }
        if (value == Math.rint(value) && value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE) {
            return Integer.toString((int) value);
        }
        var result = String.format(Locale.ROOT, "%.5f", value);
        return result.replaceFirst("0+$", "").replaceFirst("\\.$", "");
    }
}
