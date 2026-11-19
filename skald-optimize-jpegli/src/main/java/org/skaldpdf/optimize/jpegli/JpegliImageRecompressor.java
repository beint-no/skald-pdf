package org.skaldpdf.optimize.jpegli;

import no.beint.glimt.Chroma;
import no.beint.glimt.DecodeLimits;
import no.beint.glimt.FramePolicy;
import no.beint.glimt.ImageFormat;
import no.beint.glimt.JpegOptions;
import no.beint.glimt.ResizeFilter;
import no.beint.glimt.spi.DecodeTarget;
import no.beint.glimt.spi.ImageDecoder;
import no.beint.glimt.spi.ImageResizer;
import no.beint.glimt.spi.JpegEncoder;
import no.beint.glimt.spi.PixelImage;
import org.skaldpdf.image.ImageData;
import org.skaldpdf.optimize.ImageRecompressor;
import org.skaldpdf.optimize.OptimizeOptions;
import org.skaldpdf.pdf.EmbeddedImage;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.Optional;
import java.util.ServiceLoader;

/**
 * Thread-safe JPEGli encoder for {@code skald-optimize}. Create one per
 * application and reuse it across requests and virtual threads.
 */
public final class JpegliImageRecompressor implements ImageRecompressor {
    private static final long MAXIMUM_INPUT_BYTES = 64L << 20;
    private static final long MAXIMUM_OUTPUT_BYTES = 64L << 20;
    private static final long MAXIMUM_METADATA_BYTES = 4L << 20;

    private final ImageDecoder decoder;
    private final JpegEncoder encoder;
    private final ImageResizer resizer;

    public JpegliImageRecompressor() {
        var decoders = ServiceLoader.load(ImageDecoder.class).stream()
            .map(ServiceLoader.Provider::get)
            .filter(candidate -> candidate.formats().contains(ImageFormat.JPEG))
            .toList();
        if (decoders.size() != 1) {
            throw new IllegalStateException("Exactly one Glimt JPEG decoder is required");
        }
        decoder = decoders.getFirst();
        var encoders = ServiceLoader.load(JpegEncoder.class).stream().toList();
        if (encoders.size() != 1) {
            throw new IllegalStateException("Exactly one Glimt JPEG encoder is required");
        }
        encoder = encoders.getFirst().get();
        var resizers = ServiceLoader.load(ImageResizer.class).stream().toList();
        if (resizers.size() != 1) {
            throw new IllegalStateException("Exactly one Glimt image resizer is required");
        }
        resizer = resizers.getFirst().get();
    }

    @Override
    public Optional<ImageData> recompress(EmbeddedImage image, OptimizeOptions options) {
        if (image.jpeg()) {
            return image.decode().filter(ImageData::jpeg).map(decoded -> encodeJpeg(image, decoded, options));
        }
        return image.decode().map(decoded -> encodeRaster(image, decoded, options));
    }

    private ImageData encodeJpeg(EmbeddedImage embedded, ImageData source, OptimizeOptions options) {
        var input = source.samples();
        var limits = limits(Math.min(Integer.MAX_VALUE, options.maximumImagePixels()));
        try (var arena = Arena.ofConfined()) {
            var encoded = arena.allocateFrom(ValueLayout.JAVA_BYTE, input);
            var target = decodeTarget(embedded, source.width(), source.height(), options);
            // PDF DCTDecode renders coded JPEG samples directly and ignores EXIF orientation,
            // so we decode via the raw SPI without applying Orientation.apply(...).
            var pixels = decoder.decode(encoded, ImageFormat.JPEG, limits, FramePolicy.FIRST_FRAME, target, arena);
            limits.checkDimensions(pixels.width(), pixels.height(), pixels.depth() > 8 ? 8 : 4);
            pixels = resizeIfNeeded(pixels, embedded, options, limits, arena);
            var bytes = encoder.encode(pixels, jpegOptions(Math.round(options.jpegQuality() * 100)), arena);
            return ImageData.fromJpeg(bytes);
        }
    }

    private ImageData encodeRaster(EmbeddedImage embedded, ImageData source, OptimizeOptions options) {
        var samples = source.samples();
        var components = source.components();
        var pixelCount = Math.multiplyExact((long) source.width(), source.height());
        var rgbaBytes = Math.multiplyExact(pixelCount, 4L);
        var limits = limits(Math.min(Integer.MAX_VALUE, options.maximumImagePixels()));
        try (var arena = Arena.ofConfined()) {
            var pixels = arena.allocate(rgbaBytes, 1);
            var sourceIndex = 0;
            for (long targetOffset = 0; targetOffset < rgbaBytes; targetOffset += 4) {
                if (components == 1) {
                    var gray = samples[sourceIndex++];
                    pixels.set(ValueLayout.JAVA_BYTE, targetOffset, gray);
                    pixels.set(ValueLayout.JAVA_BYTE, targetOffset + 1, gray);
                    pixels.set(ValueLayout.JAVA_BYTE, targetOffset + 2, gray);
                } else {
                    pixels.set(ValueLayout.JAVA_BYTE, targetOffset, samples[sourceIndex++]);
                    pixels.set(ValueLayout.JAVA_BYTE, targetOffset + 1, samples[sourceIndex++]);
                    pixels.set(ValueLayout.JAVA_BYTE, targetOffset + 2, samples[sourceIndex++]);
                }
                pixels.set(ValueLayout.JAVA_BYTE, targetOffset + 3, (byte) 0xff);
            }
            var image = new PixelImage(source.width(), source.height(), 8, 1, 1,
                1, 13, false, Math.multiplyExact(source.width(), 4L), pixels, MemorySegment.NULL);
            image = resizeIfNeeded(image, embedded, options, limits, arena);
            var bytes = encoder.encode(image,
                jpegOptions(Math.round(options.losslessQuality() * 100)), arena);
            return ImageData.fromJpeg(bytes);
        }
    }

    private PixelImage resizeIfNeeded(PixelImage image, EmbeddedImage embedded, OptimizeOptions options,
                                      DecodeLimits limits, Arena arena) {
        if (embedded.requiresOriginalDimensions()) {
            return image;
        }
        var longest = Math.max(image.width(), image.height());
        if (longest <= options.maxEdge()) {
            return image;
        }
        var scale = options.maxEdge() / (double) longest;
        var width = Math.max(1, (int) Math.round(image.width() * scale));
        var height = Math.max(1, (int) Math.round(image.height() * scale));
        return resizer.resize(image, width, height, ResizeFilter.MITCHELL, limits, arena);
    }

    private static DecodeTarget decodeTarget(EmbeddedImage embedded, int width, int height, OptimizeOptions options) {
        if (embedded.requiresOriginalDimensions()) {
            return DecodeTarget.NONE;
        }
        var longest = Math.max(width, height);
        if (longest <= options.maxEdge()) {
            return DecodeTarget.NONE;
        }
        var scale = options.maxEdge() / (double) longest;
        return new DecodeTarget(
            Math.max(1, (int) Math.round(width * scale)),
            Math.max(1, (int) Math.round(height * scale)));
    }

    private static JpegOptions jpegOptions(int quality) {
        return new JpegOptions(quality, Chroma.YUV420, true, true, 0xffffff, MAXIMUM_OUTPUT_BYTES);
    }

    private static DecodeLimits limits(long maximumPixels) {
        var decodedBytes = Math.min(Integer.MAX_VALUE, Math.multiplyExact(maximumPixels, 4L));
        return new DecodeLimits(MAXIMUM_INPUT_BYTES, maximumPixels, decodedBytes,
            MAXIMUM_METADATA_BYTES, 65_536, 1);
    }
}
