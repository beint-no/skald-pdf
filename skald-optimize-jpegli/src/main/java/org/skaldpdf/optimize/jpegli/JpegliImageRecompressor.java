package org.skaldpdf.optimize.jpegli;

import no.beint.glimt.Chroma;
import no.beint.glimt.DecodeLimits;
import no.beint.glimt.FramePolicy;
import no.beint.glimt.JpegConverter;
import no.beint.glimt.JpegOptions;
import no.beint.glimt.ResizeFilter;
import no.beint.glimt.spi.ImageResizer;
import no.beint.glimt.spi.JpegEncoder;
import no.beint.glimt.spi.PixelImage;
import org.skaldpdf.image.ImageData;
import org.skaldpdf.optimize.ImageRecompressor;
import org.skaldpdf.optimize.OptimizeOptions;
import org.skaldpdf.pdf.EmbeddedImage;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe JPEGli encoder for {@code skald-optimize}. Create one per
 * application and reuse it across requests and virtual threads.
 */
public final class JpegliImageRecompressor implements ImageRecompressor {
    private static final long MAXIMUM_INPUT_BYTES = 64L << 20;
    private static final long MAXIMUM_OUTPUT_BYTES = 64L << 20;
    private static final long MAXIMUM_METADATA_BYTES = 4L << 20;

    private final ConcurrentHashMap<Policy, JpegConverter> jpegConverters = new ConcurrentHashMap<>();
    private final JpegEncoder encoder;
    private final ImageResizer resizer;

    public JpegliImageRecompressor() {
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
            return image.decode().filter(ImageData::jpeg).map(decoded -> {
                var converted = jpegConverters.computeIfAbsent(Policy.from(options), this::converter)
                    .convert(decoded.samples());
                return ImageData.fromJpeg(converted.bytes());
            });
        }
        return image.decode().map(decoded -> encodeRaster(decoded, options));
    }

    private JpegConverter converter(Policy policy) {
        var limits = limits(policy.maximumPixels());
        return JpegConverter.builder()
            .limits(limits)
            .frames(FramePolicy.FIRST_FRAME)
            .longestEdge(policy.maxEdge())
            .options(jpegOptions(policy.jpegQuality()))
            .build();
    }

    private ImageData encodeRaster(ImageData source, OptimizeOptions options) {
        var rgba = rgba(source);
        try (var arena = Arena.ofConfined()) {
            var pixels = arena.allocate(rgba.length, 1);
            pixels.copyFrom(MemorySegment.ofArray(rgba));
            var image = new PixelImage(source.width(), source.height(), 8, 1, 1,
                1, 13, false, Math.multiplyExact(source.width(), 4L), pixels, MemorySegment.NULL);
            var longest = Math.max(source.width(), source.height());
            if (longest > options.maxEdge()) {
                var scale = options.maxEdge() / (double) longest;
                var width = Math.max(1, (int) Math.round(source.width() * scale));
                var height = Math.max(1, (int) Math.round(source.height() * scale));
                image = resizer.resize(image, width, height, ResizeFilter.MITCHELL,
                    limits(Math.min(Integer.MAX_VALUE, options.maximumImagePixels())), arena);
            }
            var bytes = encoder.encode(image,
                jpegOptions(Math.round(options.losslessQuality() * 100)), arena);
            return ImageData.fromJpeg(bytes);
        }
    }

    private static JpegOptions jpegOptions(int quality) {
        return new JpegOptions(quality, Chroma.YUV420, true, true, 0xffffff, MAXIMUM_OUTPUT_BYTES);
    }

    private static DecodeLimits limits(long maximumPixels) {
        var decodedBytes = Math.min(Integer.MAX_VALUE, Math.multiplyExact(maximumPixels, 4));
        return new DecodeLimits(MAXIMUM_INPUT_BYTES, maximumPixels, decodedBytes,
            MAXIMUM_METADATA_BYTES, 65_536, 1);
    }

    private static byte[] rgba(ImageData image) {
        var samples = image.samples();
        var components = image.components();
        var pixels = Math.multiplyExact(image.width(), image.height());
        var rgba = new byte[Math.multiplyExact(pixels, 4)];
        for (int pixel = 0, source = 0, target = 0; pixel < pixels; pixel++) {
            if (components == 1) {
                var gray = samples[source++];
                rgba[target++] = gray;
                rgba[target++] = gray;
                rgba[target++] = gray;
            } else {
                rgba[target++] = samples[source++];
                rgba[target++] = samples[source++];
                rgba[target++] = samples[source++];
            }
            rgba[target++] = (byte) 0xff;
        }
        return rgba;
    }

    private record Policy(int maxEdge, int jpegQuality, long maximumPixels) {
        static Policy from(OptimizeOptions options) {
            return new Policy(options.maxEdge(), Math.round(options.jpegQuality() * 100),
                Math.min(Integer.MAX_VALUE, options.maximumImagePixels()));
        }
    }
}
