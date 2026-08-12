package org.skaldpdf.image;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Objects;

/** Immutable raster image prepared for direct PDF embedding. */
public final class ImageData implements ImageSource {
    private static final long MAXIMUM_PIXELS = 100_000_000L;

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
}
