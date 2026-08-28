package org.skaldpdf.codec;

import org.jspecify.annotations.Nullable;
import org.skaldpdf.image.ImageData;

import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import java.awt.Color;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/** Standard JDK decoding and transformation of JPEG, PNG, GIF, and BMP images. */
public final class RasterImages {
    private static final long MAXIMUM_PIXELS = 100_000_000L;
    private static final int MAXIMUM_ENCODED_BYTES = 32 * 1024 * 1024;

    private RasterImages() {
    }

    public static ImageData decode(byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes");
        if (bytes.length == 0) {
            throw new IllegalArgumentException("Image data is empty");
        }
        if (bytes.length > MAXIMUM_ENCODED_BYTES) {
            throw new IllegalArgumentException("Image data exceeds the safe encoded-size limit");
        }
        var header = inspect(bytes);
        requireSafeDimensions(header.width, header.height);
        try {
            var image = ImageIO.read(new ByteArrayInputStream(bytes));
            if (image == null) {
                throw new IllegalArgumentException("Unsupported or invalid image data");
            }
            requireSafeDimensions(image.getWidth(), image.getHeight());
            if (header.jpeg && !image.getColorModel().hasAlpha()
                && (header.components == 1 || header.components == 3)) {
                return ImageData.fromJpeg(bytes);
            }
            return fromBufferedImage(image);
        } catch (IOException exception) {
            throw new IllegalArgumentException("Unable to decode image data", exception);
        }
    }

    public static ImageData decode(InputStream input) {
        Objects.requireNonNull(input, "input");
        try {
            return decode(input.readAllBytes());
        } catch (IOException exception) {
            throw new IllegalArgumentException("Unable to read image", exception);
        }
    }

    public static ImageData decode(Path path) {
        try (var input = Files.newInputStream(Objects.requireNonNull(path, "path"))) {
            return decode(input);
        } catch (IOException exception) {
            throw new IllegalArgumentException("Unable to read image", exception);
        }
    }

    /** Downscales an image so both edges fit within the requested dimensions. */
    public static ImageData scaleToFit(ImageData source, int maxWidth, int maxHeight) {
        Objects.requireNonNull(source, "source");
        if (maxWidth < 1 || maxHeight < 1) {
            throw new IllegalArgumentException("Maximum image size must be at least 1×1");
        }
        if (source.width() <= maxWidth && source.height() <= maxHeight) {
            return source;
        }
        var scale = Math.min(maxWidth / (double) source.width(), maxHeight / (double) source.height());
        var targetWidth = Math.max(1, (int) Math.round(source.width() * scale));
        var targetHeight = Math.max(1, (int) Math.round(source.height() * scale));
        return rasterize(source, targetWidth, targetHeight, source.jpeg(), 0.82f);
    }

    /** Re-encodes an image as JPEG and composites alpha on white. */
    public static ImageData asJpeg(ImageData source, float quality) {
        Objects.requireNonNull(source, "source");
        return asJpeg(source, source.width(), source.height(), quality);
    }

    /** Downscales and JPEG-encodes in one raster pass, compositing alpha on white. */
    public static ImageData asJpeg(ImageData source, int maxWidth, int maxHeight, float quality) {
        Objects.requireNonNull(source, "source");
        if (maxWidth < 1 || maxHeight < 1) {
            throw new IllegalArgumentException("Maximum image size must be at least 1×1");
        }
        if (!(quality > 0) || quality > 1 || !Float.isFinite(quality)) {
            throw new IllegalArgumentException("JPEG quality must be in (0, 1]");
        }
        var scale = Math.min(1, Math.min(maxWidth / (double) source.width(), maxHeight / (double) source.height()));
        var width = Math.max(1, (int) Math.round(source.width() * scale));
        var height = Math.max(1, (int) Math.round(source.height() * scale));
        return rasterize(source, width, height, true, quality);
    }

    private static ImageData rasterize(ImageData source, int width, int height, boolean jpeg, float quality) {
        var input = toBufferedImage(source);
        var output = new BufferedImage(width, height,
            jpeg || source.alpha() == null ? BufferedImage.TYPE_INT_RGB : BufferedImage.TYPE_INT_ARGB);
        var graphics = output.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        if (jpeg || source.alpha() == null) {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, width, height);
        }
        graphics.drawImage(input, 0, 0, width, height, null);
        graphics.dispose();
        if (!jpeg) {
            return fromBufferedImage(output);
        }
        try {
            var bytes = new ByteArrayOutputStream();
            var writers = ImageIO.getImageWritersByFormatName("jpeg");
            if (!writers.hasNext()) {
                throw new IllegalStateException("No JPEG writer is available");
            }
            var writer = writers.next();
            var parameters = writer.getDefaultWriteParam();
            parameters.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            parameters.setCompressionQuality(quality);
            try (var stream = ImageIO.createImageOutputStream(bytes)) {
                writer.setOutput(stream);
                writer.write(null, new javax.imageio.IIOImage(output, null, null), parameters);
            } finally {
                writer.dispose();
            }
            return ImageData.fromJpeg(bytes.toByteArray());
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to re-encode image", exception);
        }
    }

    static BufferedImage toBufferedImage(ImageData source) {
        if (source.jpeg()) {
            try {
                var image = ImageIO.read(new ByteArrayInputStream(source.samples()));
                if (image == null) {
                    throw new IllegalStateException("Unable to decode JPEG samples");
                }
                return image;
            } catch (IOException exception) {
                throw new IllegalStateException("Unable to decode JPEG samples", exception);
            }
        }
        var width = source.width();
        var height = source.height();
        var alpha = source.alpha();
        var samples = source.samples();
        var pixels = new int[Math.multiplyExact(width, height)];
        var gray = source.components() == 1;
        var sampleOffset = 0;
        for (int index = 0; index < pixels.length; index++) {
            int red;
            int green;
            int blue;
            if (gray) {
                red = green = blue = samples[sampleOffset++] & 0xff;
            } else {
                red = samples[sampleOffset++] & 0xff;
                green = samples[sampleOffset++] & 0xff;
                blue = samples[sampleOffset++] & 0xff;
            }
            var alphaValue = alpha == null ? 255 : alpha[index] & 0xff;
            pixels[index] = alphaValue << 24 | red << 16 | green << 8 | blue;
        }
        var type = alpha == null ? BufferedImage.TYPE_INT_RGB : BufferedImage.TYPE_INT_ARGB;
        var image = new BufferedImage(width, height, type);
        var dest = argbPixels(image, true);
        if (dest != null) {
            System.arraycopy(pixels, 0, dest, 0, pixels.length);
        } else {
            image.setRGB(0, 0, width, height, pixels, 0, width);
        }
        return image;
    }

    static ImageData fromBufferedImage(BufferedImage image) {
        var width = image.getWidth();
        var height = image.getHeight();
        var pixels = argbPixels(image, false);
        var owned = pixels;
        if (owned == null) {
            owned = image.getRGB(0, 0, width, height, null, 0, width);
        }
        var rgb = new byte[Math.multiplyExact(owned.length, 3)];
        var hasAlpha = image.getColorModel().hasAlpha();
        var alpha = hasAlpha ? new byte[owned.length] : null;
        unpackArgb(owned, rgb, alpha);
        return alpha == null
            ? ImageData.fromRgb(width, height, rgb)
            : ImageData.fromRgb(width, height, rgb, alpha);
    }

    /**
     * Direct ARGB/RGB int storage when the image is already a packed int raster.
     * Returns {@code null} when a copy via {@code getRGB} is required.
     */
    static int @Nullable [] argbPixels(BufferedImage image, boolean requireWritable) {
        if (image.getType() != BufferedImage.TYPE_INT_ARGB
            && image.getType() != BufferedImage.TYPE_INT_RGB
            && image.getType() != BufferedImage.TYPE_INT_ARGB_PRE) {
            return null;
        }
        if (!(image.getRaster().getDataBuffer() instanceof DataBufferInt buffer)) {
            return null;
        }
        if (requireWritable && buffer.getNumBanks() != 1) {
            return null;
        }
        var data = buffer.getData();
        return data.length >= image.getWidth() * image.getHeight() ? data : null;
    }

    /** Unpacks packed ARGB ints into DeviceRGB bytes and an optional alpha plane. */
    static void unpackArgb(int[] pixels, byte[] rgb, byte @Nullable [] alpha) {
        var sampleOffset = 0;
        if (alpha == null) {
            for (var pixel : pixels) {
                rgb[sampleOffset++] = (byte) (pixel >>> 16);
                rgb[sampleOffset++] = (byte) (pixel >>> 8);
                rgb[sampleOffset++] = (byte) pixel;
            }
            return;
        }
        for (int index = 0; index < pixels.length; index++) {
            var pixel = pixels[index];
            rgb[sampleOffset++] = (byte) (pixel >>> 16);
            rgb[sampleOffset++] = (byte) (pixel >>> 8);
            rgb[sampleOffset++] = (byte) pixel;
            alpha[index] = (byte) (pixel >>> 24);
        }
    }

    /** Per-pixel ImageIO accessors kept as the measured baseline, not the production path. */
    static ImageData fromBufferedImagePixelAtATime(BufferedImage image) {
        var width = image.getWidth();
        var height = image.getHeight();
        var rgb = new byte[Math.multiplyExact(Math.multiplyExact(width, height), 3)];
        var alpha = image.getColorModel().hasAlpha() ? new byte[Math.multiplyExact(width, height)] : null;
        var sampleOffset = 0;
        var alphaOffset = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                var pixel = image.getRGB(x, y);
                rgb[sampleOffset++] = (byte) (pixel >>> 16);
                rgb[sampleOffset++] = (byte) (pixel >>> 8);
                rgb[sampleOffset++] = (byte) pixel;
                if (alpha != null) {
                    alpha[alphaOffset++] = (byte) (pixel >>> 24);
                }
            }
        }
        return alpha == null
            ? ImageData.fromRgb(width, height, rgb)
            : ImageData.fromRgb(width, height, rgb, alpha);
    }

    private static Header inspect(byte[] bytes) {
        if (bytes.length >= 3 && (bytes[0] & 0xff) == 0xff && (bytes[1] & 0xff) == 0xd8) {
            return jpegHeader(bytes);
        }
        if (bytes.length >= 24
            && bytes[0] == (byte) 0x89 && bytes[1] == 0x50 && bytes[2] == 0x4e && bytes[3] == 0x47
            && bytes[4] == 0x0d && bytes[5] == 0x0a && bytes[6] == 0x1a && bytes[7] == 0x0a) {
            return new Header(readInt32Be(bytes, 16), readInt32Be(bytes, 20), 0, false);
        }
        if (bytes.length >= 10 && (startsWith(bytes, "GIF87a") || startsWith(bytes, "GIF89a"))) {
            return new Header(readU16Le(bytes, 6), readU16Le(bytes, 8), 0, false);
        }
        if (bytes.length >= 26 && bytes[0] == 'B' && bytes[1] == 'M') {
            return new Header(readInt32Le(bytes, 18), Math.abs(readInt32Le(bytes, 22)), 0, false);
        }
        throw new IllegalArgumentException("Unsupported or invalid image data");
    }

    private static Header jpegHeader(byte[] bytes) {
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
            if (marker == 0x01 || marker >= 0xd0 && marker <= 0xd8) {
                continue;
            }
            if (index + 1 >= bytes.length) {
                break;
            }
            var length = (bytes[index] & 0xff) << 8 | bytes[index + 1] & 0xff;
            if (length < 2 || index + length > bytes.length) {
                throw new IllegalArgumentException("Truncated JPEG");
            }
            if (marker >= 0xc0 && marker <= 0xcf && marker != 0xc4 && marker != 0xc8 && marker != 0xcc) {
                if (length < 8) {
                    throw new IllegalArgumentException("Truncated JPEG");
                }
                return new Header(
                    (bytes[index + 5] & 0xff) << 8 | bytes[index + 6] & 0xff,
                    (bytes[index + 3] & 0xff) << 8 | bytes[index + 4] & 0xff,
                    bytes[index + 7] & 0xff,
                    true);
            }
            index += length;
        }
        throw new IllegalArgumentException("JPEG has no frame header");
    }

    private static void requireSafeDimensions(int width, int height) {
        if (width <= 0 || height <= 0 || (long) width * height > MAXIMUM_PIXELS) {
            throw new IllegalArgumentException("Image dimensions exceed the safe decoding limit");
        }
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
        return (bytes[offset] & 0xff) << 24
            | (bytes[offset + 1] & 0xff) << 16
            | (bytes[offset + 2] & 0xff) << 8
            | bytes[offset + 3] & 0xff;
    }

    private static int readInt32Le(byte[] bytes, int offset) {
        return bytes[offset] & 0xff
            | (bytes[offset + 1] & 0xff) << 8
            | (bytes[offset + 2] & 0xff) << 16
            | (bytes[offset + 3] & 0xff) << 24;
    }

    private static int readU16Le(byte[] bytes, int offset) {
        return bytes[offset] & 0xff | (bytes[offset + 1] & 0xff) << 8;
    }

    private record Header(int width, int height, int components, boolean jpeg) {
    }
}
