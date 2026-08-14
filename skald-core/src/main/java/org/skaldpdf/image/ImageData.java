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

    /**
     * Packed 8-bit DeviceRGB samples, top-down, three bytes per pixel.
     * Use this when a native codec has already decoded a photo.
     */
    public static ImageData fromRgb(int width, int height, byte[] rgb) {
        return fromRaster(width, height, 3, rgb);
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

    private ImageData(byte[] samples, byte[] alpha, int width, int height, int components, boolean jpeg) {
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

    /**
     * Downscale so both edges fit {@code maxWidth}×{@code maxHeight}. Photos attached
     * to expense claims should be reduced before embedding.
     */
    public ImageData scaledToFit(int maxWidth, int maxHeight) {
        if (maxWidth < 1 || maxHeight < 1) {
            throw new IllegalArgumentException("Maximum image size must be at least 1×1");
        }
        if (width <= maxWidth && height <= maxHeight) {
            return this;
        }
        var scale = Math.min(maxWidth / (double) width, maxHeight / (double) height);
        var targetWidth = Math.max(1, (int) Math.round(width * scale));
        var targetHeight = Math.max(1, (int) Math.round(height * scale));
        return rasterize(targetWidth, targetHeight, jpeg);
    }

    /**
     * Re-encode as JPEG. Alpha is composited on white. Used when a PNG photo
     * should be stored as a DCT stream.
     */
    public ImageData asJpeg(float quality) {
        if (!(quality > 0) || quality > 1 || !Float.isFinite(quality)) {
            throw new IllegalArgumentException("JPEG quality must be in (0, 1]");
        }
        return rasterize(width, height, true, quality);
    }

    private ImageData rasterize(int targetWidth, int targetHeight, boolean asJpeg) {
        return rasterize(targetWidth, targetHeight, asJpeg, 0.82f);
    }

    private ImageData rasterize(int targetWidth, int targetHeight, boolean asJpeg, float quality) {
        var source = toBufferedImage();
        var scaled = new java.awt.image.BufferedImage(targetWidth, targetHeight,
            asJpeg || alpha == null
                ? java.awt.image.BufferedImage.TYPE_INT_RGB
                : java.awt.image.BufferedImage.TYPE_INT_ARGB);
        var graphics = scaled.createGraphics();
        graphics.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
            java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        if (asJpeg || alpha == null) {
            graphics.setColor(java.awt.Color.WHITE);
            graphics.fillRect(0, 0, targetWidth, targetHeight);
        }
        graphics.drawImage(source, 0, 0, targetWidth, targetHeight, null);
        graphics.dispose();
        try {
            var output = new java.io.ByteArrayOutputStream();
            if (asJpeg) {
                var writers = javax.imageio.ImageIO.getImageWritersByFormatName("jpeg");
                if (!writers.hasNext()) {
                    throw new IllegalStateException("No JPEG writer is available");
                }
                var writer = writers.next();
                var params = writer.getDefaultWriteParam();
                params.setCompressionMode(javax.imageio.ImageWriteParam.MODE_EXPLICIT);
                params.setCompressionQuality(quality);
                try (var ios = javax.imageio.ImageIO.createImageOutputStream(output)) {
                    writer.setOutput(ios);
                    writer.write(null, new javax.imageio.IIOImage(scaled, null, null), params);
                } finally {
                    writer.dispose();
                }
            } else {
                javax.imageio.ImageIO.write(scaled, "png", output);
            }
            return new ImageData(output.toByteArray());
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("Unable to re-encode image", exception);
        }
    }

    private java.awt.image.BufferedImage toBufferedImage() {
        if (jpeg) {
            try {
                var image = javax.imageio.ImageIO.read(new ByteArrayInputStream(samples));
                if (image == null) {
                    throw new IllegalStateException("Unable to decode JPEG samples");
                }
                return image;
            } catch (IOException exception) {
                throw new IllegalStateException("Unable to decode JPEG samples", exception);
            }
        }
        var image = new java.awt.image.BufferedImage(width, height,
            alpha == null ? java.awt.image.BufferedImage.TYPE_INT_RGB : java.awt.image.BufferedImage.TYPE_INT_ARGB);
        var offset = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int red;
                int green;
                int blue;
                if (components == 1) {
                    red = green = blue = samples[offset++] & 0xff;
                } else {
                    red = samples[offset++] & 0xff;
                    green = samples[offset++] & 0xff;
                    blue = samples[offset++] & 0xff;
                }
                var alphaValue = this.alpha == null ? 255 : (this.alpha[y * width + x] & 0xff);
                image.setRGB(x, y, (alphaValue << 24) | (red << 16) | (green << 8) | blue);
            }
        }
        return image;
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
