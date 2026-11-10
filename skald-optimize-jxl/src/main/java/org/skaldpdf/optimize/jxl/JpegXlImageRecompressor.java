package org.skaldpdf.optimize.jxl;

import org.skaldpdf.codec.JpegXlOptions;
import org.skaldpdf.codec.NativeImages;
import org.skaldpdf.codec.RasterImages;
import org.skaldpdf.image.ImageData;
import org.skaldpdf.image.ImageEncoding;
import org.skaldpdf.optimize.ImageRecompressor;
import org.skaldpdf.optimize.OptimizeOptions;
import org.skaldpdf.pdf.EmbeddedImage;

import java.util.Objects;
import java.util.Optional;

/** Experimental /JXLDecode adapter; requires explicit opt-in and future-compatible viewers. */
public final class JpegXlImageRecompressor implements ImageRecompressor {
    private final JpegXlOptions photographs;
    private final JpegXlOptions rasters;

    /** Preserves exact raster samples and uses reversible recompression for unchanged JPEGs. */
    public JpegXlImageRecompressor() {
        this(JpegXlOptions.lossless(), JpegXlOptions.lossless());
    }

    /** Separates existing JPEG/photo quality from originally lossless graphics/scans. */
    public JpegXlImageRecompressor(JpegXlOptions photographs, JpegXlOptions rasters) {
        this.photographs = Objects.requireNonNull(photographs, "photographs");
        this.rasters = Objects.requireNonNull(rasters, "rasters");
    }

    @Override
    public ImageEncoding encoding() {
        return ImageEncoding.EXPERIMENTAL_JPEG_XL;
    }

    @Override
    public Optional<ImageData> recompress(EmbeddedImage image, OptimizeOptions options) {
        // The provisional PDF contract for ICC/CMYK, embedded alpha and high precision
        // is unsettled. Preserve those originals rather than guess their color semantics.
        if (!NativeImages.jpegXlEncodingAvailable()
                || image.jpegXl()
                || !image.safeToRecompress()
                || !image.colorSpace().equals("DeviceRGB")
                        && !image.colorSpace().equals("DeviceGray")) {
            return Optional.empty();
        }
        var settings = image.jpeg() ? photographs : rasters;
        if (image.jpeg() && image.colorSpace().equals("DeviceGray") && settings.distance() != 0) {
            return Optional
                    .empty(); // Avoid implicit gray-to-sRGB conversion during lossy JPEG ingestion.
        }
        var decoded = image.decode().orElse(null);
        if (decoded == null) return Optional.empty();
        var resize =
                !image.requiresOriginalDimensions()
                        && Math.max(image.width(), image.height()) > options.maxEdge();
        if (resize) decoded = resize(RasterImages.decodeSamples(decoded), options.maxEdge());
        var candidate = NativeImages.jpegXlEncode(decoded, settings);
        if (image.jpeg() && !resize && settings.distance() > 0) {
            var reversible =
                    NativeImages.jpegXlEncode(decoded, new JpegXlOptions(0, settings.effort()));
            if (reversible.samples().length <= candidate.samples().length) candidate = reversible;
        }
        return Optional.of(candidate);
    }

    private static ImageData resize(ImageData source, int maxEdge) {
        var factor = (double) maxEdge / Math.max(source.width(), source.height());
        var width = Math.max(1, (int) Math.round(source.width() * factor));
        var height = Math.max(1, (int) Math.round(source.height() * factor));
        var components = source.components();
        var input = source.samples();
        var output = new byte[width * height * components];
        for (int y = 0; y < height; y++)
            for (int x = 0; x < width; x++) {
                var left = (double) x * source.width() / width;
                var right = (double) (x + 1) * source.width() / width;
                var top = (double) y * source.height() / height;
                var bottom = (double) (y + 1) * source.height() / height;
                for (int channel = 0; channel < components; channel++) {
                    double sum = 0, area = 0;
                    for (int iy = (int) top; iy < Math.ceil(bottom); iy++)
                        for (int ix = (int) left; ix < Math.ceil(right); ix++) {
                            var weight =
                                    (Math.min(right, ix + 1) - Math.max(left, ix))
                                            * (Math.min(bottom, iy + 1) - Math.max(top, iy));
                            sum +=
                                    (input[(iy * source.width() + ix) * components + channel] & 255)
                                            * weight;
                            area += weight;
                        }
                    output[(y * width + x) * components + channel] = (byte) Math.round(sum / area);
                }
            }
        return components == 1
                ? ImageData.fromGray(width, height, output)
                : ImageData.fromRgb(width, height, output);
    }
}
