package org.skaldpdf.codec;

import org.skaldpdf.image.ImageData;

import java.util.Objects;

/**
 * Optional native codecs (TurboJPEG, libheif, libjxl) via the JDK FFM API.
 *
 * <p>Missing libraries are not fatal: {@link #jpegAvailable()} / {@link #heifAvailable()}
 * / {@link #jpegXlAvailable()} report what this machine can do. Core PDF
 * generation never requires this module. JPEG XL is decode-only ingest —
 * prepared output is always a DCT JPEG that current PDF viewers can display.
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

    public static boolean jpegXlAvailable() {
        return JpegXl.AVAILABLE;
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

    public static Raster jpegXlDecode(byte[] jxl) {
        return JpegXl.decode(jxl);
    }

    /**
     * Decode a JPEG, HEIC/AVIF, or JPEG XL photo, shrink it, and return an
     * {@link ImageData} that Skald can embed as a DCT stream. JPEG XL is never
     * stored inside the PDF.
     */
    public static ImageData prepare(byte[] bytes, PrepareOptions options) {
        Objects.requireNonNull(bytes, "bytes");
        Objects.requireNonNull(options, "options");
        var raster = decode(bytes);
        raster = scaleToFit(raster, options.maxEdge());
        if (TurboJpeg.AVAILABLE) {
            return ImageData.fromJpeg(TurboJpeg.compress(raster, options.jpegQuality()));
        }
        return RasterImages.asJpeg(ImageData.fromRgb(raster.width(), raster.height(), raster.rgb()),
            options.jpegQuality() / 100f);
    }

    static Raster decode(byte[] bytes) {
        if (isJpeg(bytes)) {
            return TurboJpeg.decompress(bytes);
        }
        if (isHeif(bytes)) {
            return Heif.decode(bytes);
        }
        if (isJpegXl(bytes)) {
            return JpegXl.decode(bytes);
        }
        throw new IllegalArgumentException("NativeImages.prepare accepts JPEG, HEIC/AVIF, or JPEG XL");
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

    static boolean isJpegXl(byte[] bytes) {
        if (bytes.length >= 2 && (bytes[0] & 0xff) == 0xff && bytes[1] == 0x0a) {
            return true;
        }
        return bytes.length >= 12
            && bytes[0] == 0 && bytes[1] == 0 && bytes[2] == 0 && bytes[3] == 0x0c
            && bytes[4] == 'J' && bytes[5] == 'X' && bytes[6] == 'L' && bytes[7] == ' ';
    }
}
