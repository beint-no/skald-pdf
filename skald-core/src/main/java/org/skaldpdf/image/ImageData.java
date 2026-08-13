package org.skaldpdf.image;

import org.skaldpdf.pdf.PdfDocument;
import org.skaldpdf.pdf.PdfPage;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Locale;
import java.util.Objects;

/** Immutable raster image prepared for direct PDF embedding. */
public final class ImageData implements ImageSource {
    private static final long MAXIMUM_PIXELS = 100_000_000L;
    private static final int MAXIMUM_ENCODED_BYTES = 32 * 1024 * 1024;

    private final byte[] samples;
    private final byte[] alpha;
    private final int width;
    private final int height;
    private final int components;
    private final boolean jpeg;

    ImageData(byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes");
        if (bytes.length == 0) {
            throw new IllegalArgumentException("Image data is empty");
        }
        if (bytes.length > MAXIMUM_ENCODED_BYTES) {
            throw new IllegalArgumentException("Image data exceeds the safe encoded-size limit");
        }
        var header = inspect(bytes);
        if (header.width <= 0 || header.height <= 0
            || (long) header.width * header.height > MAXIMUM_PIXELS) {
            throw new IllegalArgumentException("Image dimensions exceed the safe decoding limit");
        }
        try {
            var image = ImageIO.read(new ByteArrayInputStream(bytes));
            if (image == null) {
                throw new IllegalArgumentException("Unsupported or invalid image data");
            }
            width = image.getWidth();
            height = image.getHeight();
            if (width <= 0 || height <= 0 || (long) width * height > MAXIMUM_PIXELS) {
                throw new IllegalArgumentException("Image dimensions exceed the safe decoding limit");
            }
            var colorComponents = image.getColorModel().getNumColorComponents();
            jpeg = bytes.length >= 2 && (bytes[0] & 0xff) == 0xff && (bytes[1] & 0xff) == 0xd8
                && !image.getColorModel().hasAlpha() && (colorComponents == 1 || colorComponents == 3);
            if (jpeg) {
                samples = bytes.clone();
                alpha = null;
                components = colorComponents;
            } else {
                components = 3;
                var rgb = new byte[Math.multiplyExact(Math.multiplyExact(width, height), components)];
                var transparency = image.getColorModel().hasAlpha()
                    ? new byte[Math.multiplyExact(width, height)] : null;
                var sampleOffset = 0;
                var alphaOffset = 0;
                for (int y = 0; y < height; y++) {
                    for (int x = 0; x < width; x++) {
                        var pixel = image.getRGB(x, y);
                        rgb[sampleOffset++] = (byte) (pixel >>> 16);
                        rgb[sampleOffset++] = (byte) (pixel >>> 8);
                        rgb[sampleOffset++] = (byte) pixel;
                        if (transparency != null) {
                            transparency[alphaOffset++] = (byte) (pixel >>> 24);
                        }
                    }
                }
                samples = rgb;
                alpha = transparency != null && isOpaque(transparency) ? null : transparency;
            }
        } catch (IOException exception) {
            throw new IllegalArgumentException("Unable to decode image data", exception);
        }
    }

    private static ImageHeader inspect(byte[] bytes) {
        if (bytes.length >= 3 && (bytes[0] & 0xff) == 0xff && (bytes[1] & 0xff) == 0xd8) {
            return jpegDimensions(bytes);
        }
        if (bytes.length >= 24
            && bytes[0] == (byte) 0x89 && bytes[1] == 0x50 && bytes[2] == 0x4e && bytes[3] == 0x47
            && bytes[4] == 0x0d && bytes[5] == 0x0a && bytes[6] == 0x1a && bytes[7] == 0x0a) {
            return new ImageHeader(readInt32Be(bytes, 16), readInt32Be(bytes, 20));
        }
        if (bytes.length >= 10 && (startsWith(bytes, "GIF87a") || startsWith(bytes, "GIF89a"))) {
            return new ImageHeader(readU16Le(bytes, 6), readU16Le(bytes, 8));
        }
        if (bytes.length >= 26 && bytes[0] == 'B' && bytes[1] == 'M') {
            return new ImageHeader(readInt32Le(bytes, 18), Math.abs(readInt32Le(bytes, 22)));
        }
        throw new IllegalArgumentException("Unsupported or invalid image data");
    }

    private static ImageHeader jpegDimensions(byte[] bytes) {
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
                if (length < 7) {
                    throw new IllegalArgumentException("Truncated JPEG");
                }
                var height = ((bytes[index + 3] & 0xff) << 8) | (bytes[index + 4] & 0xff);
                var width = ((bytes[index + 5] & 0xff) << 8) | (bytes[index + 6] & 0xff);
                return new ImageHeader(width, height);
            }
            index += length;
        }
        throw new IllegalArgumentException("JPEG has no frame header");
    }

    private static boolean startsWith(byte[] bytes, String prefix) {
        if (bytes.length < prefix.length()) {
            return false;
        }
        for (int index = 0; index < prefix.length(); index++) {
            if (bytes[index] != prefix.charAt(index)) {
                return false;
            }
        }
        return true;
    }

    private static int readInt32Be(byte[] bytes, int offset) {
        return ((bytes[offset] & 0xff) << 24)
            | ((bytes[offset + 1] & 0xff) << 16)
            | ((bytes[offset + 2] & 0xff) << 8)
            | (bytes[offset + 3] & 0xff);
    }

    private static int readInt32Le(byte[] bytes, int offset) {
        return (bytes[offset] & 0xff)
            | ((bytes[offset + 1] & 0xff) << 8)
            | ((bytes[offset + 2] & 0xff) << 16)
            | ((bytes[offset + 3] & 0xff) << 24);
    }

    private static int readU16Le(byte[] bytes, int offset) {
        return (bytes[offset] & 0xff) | ((bytes[offset + 1] & 0xff) << 8);
    }

    private record ImageHeader(int width, int height) {
    }

    private static boolean isOpaque(byte[] alpha) {
        for (var value : alpha) {
            if ((value & 0xff) != 0xff) {
                return false;
            }
        }
        return true;
    }

    public byte[] samples() {
        return samples.clone();
    }

    public byte[] alpha() {
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
