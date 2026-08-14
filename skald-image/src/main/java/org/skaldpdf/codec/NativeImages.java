package org.skaldpdf.codec;

import org.skaldpdf.image.ImageData;
import org.skaldpdf.image.ImageDataFactory;

import java.util.Objects;

/**
 * Optional native codecs (TurboJPEG, libheif) via the JDK FFM API.
 *
 * <p>Missing libraries are not fatal: {@link #jpegAvailable()} / {@link #heifAvailable()}
 * report what this machine can do. Core PDF generation never requires this module.
 */
public final class NativeImages {
    private NativeImages() {
    }

    public static boolean jpegAvailable() {
        return TurboJpeg.AVAILABLE;
    }

    public static boolean heifAvailable() {
        return Heif.AVAILABLE;
    }

    public static byte[] jpegEncode(Raster raster, int quality) {
        return TurboJpeg.compress(raster, quality);
    }

    public static Raster jpegDecode(byte[] jpeg) {
        return TurboJpeg.decompress(jpeg);
    }

    public static Raster heifDecode(byte[] heif) {
        return Heif.decode(heif);
    }

    /**
     * Decode a JPEG or HEIC/AVIF photo, shrink it, and return an {@link ImageData}
     * that Skald can embed as a DCT stream.
     */
    public static ImageData prepare(byte[] bytes, PrepareOptions options) {
        Objects.requireNonNull(bytes, "bytes");
        Objects.requireNonNull(options, "options");
        var raster = decode(bytes);
        raster = scaleToFit(raster, options.maxEdge());
        var jpeg = TurboJpeg.compress(raster, options.jpegQuality());
        return ImageDataFactory.create(jpeg);
    }

    static Raster decode(byte[] bytes) {
        if (isJpeg(bytes)) {
            return TurboJpeg.decompress(bytes);
        }
        if (isHeif(bytes)) {
            return Heif.decode(bytes);
        }
        throw new IllegalArgumentException("NativeImages.prepare accepts JPEG or HEIC/AVIF");
    }

    static Raster scaleToFit(Raster source, int maxEdge) {
        if (source.width() <= maxEdge && source.height() <= maxEdge) {
            return source;
        }
        var scale = Math.min(maxEdge / (double) source.width(), maxEdge / (double) source.height());
        var width = Math.max(1, (int) Math.round(source.width() * scale));
        var height = Math.max(1, (int) Math.round(source.height() * scale));
        var rgb = new byte[Math.multiplyExact(Math.multiplyExact(width, height), 3)];
        var src = source.rgb();
        var srcWidth = source.width();
        var srcHeight = source.height();
        for (int y = 0; y < height; y++) {
            var srcY = Math.min(srcHeight - 1, (int) ((y + 0.5) * srcHeight / height));
            for (int x = 0; x < width; x++) {
                var srcX = Math.min(srcWidth - 1, (int) ((x + 0.5) * srcWidth / width));
                var from = (srcY * srcWidth + srcX) * 3;
                var to = (y * width + x) * 3;
                rgb[to] = src[from];
                rgb[to + 1] = src[from + 1];
                rgb[to + 2] = src[from + 2];
            }
        }
        return new Raster(width, height, rgb);
    }

    static boolean isJpeg(byte[] bytes) {
        return bytes.length >= 2 && (bytes[0] & 0xff) == 0xff && (bytes[1] & 0xff) == 0xd8;
    }

    static boolean isHeif(byte[] bytes) {
        if (bytes.length < 12) {
            return false;
        }
        if (bytes[4] != 'f' || bytes[5] != 't' || bytes[6] != 'y' || bytes[7] != 'p') {
            return false;
        }
        for (int offset = 8; offset + 4 <= Math.min(bytes.length, 24); offset += 4) {
            var brand = new String(bytes, offset, 4, java.nio.charset.StandardCharsets.US_ASCII);
            if (brand.equals("heic") || brand.equals("heix") || brand.equals("mif1")
                || brand.equals("msf1") || brand.equals("avif") || brand.equals("avis")) {
                return true;
            }
        }
        return false;
    }
}
