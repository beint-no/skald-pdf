package org.skaldpdf.optimize.jxl;

import org.skaldpdf.codec.JpegXlOptions;
import org.skaldpdf.codec.NativeImages;
import org.skaldpdf.image.ImageData;
import org.skaldpdf.image.JpegXlCodecs;
import org.skaldpdf.pdf.PdfDocument;
import org.skaldpdf.pdf.PdfReader;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

import javax.imageio.ImageIO;

/** Offline, aggregate-only benchmark. Private PDFs/images are never copied into the repository. */
public final class JpegXlBenchmark {
    private JpegXlBenchmark() {}

    public static void main(String[] args) throws Exception {
        var corpus = Path.of(args.length == 0 ? "benchmark-corpus/largest-250" : args[0]);
        var inputs = new ArrayList<ImageData>();
        var seen = new HashSet<String>();
        try (var paths = Files.walk(corpus)) {
            for (var file : paths.filter(p -> p.toString().endsWith(".pdf")).sorted().toList()) {
                try (var document = new PdfDocument(new PdfReader(file))) {
                    for (var image : document.importedImages()) {
                        if (!image.safeToRecompress()
                                || !image.colorSpace().equals("DeviceRGB")
                                || (long) image.width() * image.height() > 4_000_000
                                || image.width() < 256
                                || image.height() < 256) continue;
                        var id =
                                HexFormat.of()
                                        .formatHex(
                                                MessageDigest.getInstance("SHA-256")
                                                        .digest(image.encodedBytes()));
                        if (seen.add(id)) {
                            var decoded = image.decode().orElse(null);
                            if (decoded != null) {
                                try {
                                    NativeImages.jpegXlEncode(decoded, JpegXlOptions.lossless());
                                    inputs.add(decoded);
                                } catch (RuntimeException unsupported) {
                                    /* profile/codec subset */
                                }
                            }
                        }
                        if (inputs.size() == 6) break;
                    }
                } catch (RuntimeException unsupported) {
                    /* unsupported source is not part of this image subset */
                }
                if (inputs.size() == 6) break;
            }
        }
        if (inputs.isEmpty()) throw new IllegalArgumentException("No eligible DeviceRGB images");
        var policies =
                Boolean.getBoolean("skald.benchmark.losslessOnly")
                        ? List.of(JpegXlOptions.lossless())
                        : List.of(
                                JpegXlOptions.lossless(),
                                JpegXlOptions.highQuality(),
                                JpegXlOptions.photos(),
                                JpegXlOptions.compact());
        for (var policy : policies) NativeImages.jpegXlEncode(inputs.getFirst(), policy);
        var csv =
                new StringBuilder(
                        "sample,width,height,source_jpeg,source_bytes,distance,jxl_bytes,saving_percent,median_encode_ms,psnr_db,source_pdf_bytes,optimized_pdf_bytes\n");
        int sample = 0;
        for (var input : inputs) {
            sample++;
            byte[] reference;
            if (input.jpeg()) {
                var bitmap = ImageIO.read(new ByteArrayInputStream(input.samples()));
                var argb =
                        bitmap.getRGB(0, 0, input.width(), input.height(), null, 0, input.width());
                reference = new byte[argb.length * 3];
                int i = 0;
                for (var pixel : argb) {
                    reference[i++] = (byte) (pixel >>> 16);
                    reference[i++] = (byte) (pixel >>> 8);
                    reference[i++] = (byte) pixel;
                }
            } else reference = input.samples();
            for (var policy : policies) {
                var nanos = new long[3];
                ImageData encoded = null;
                for (int repeat = 0; repeat < 3; repeat++) {
                    var start = System.nanoTime();
                    encoded = NativeImages.jpegXlEncode(input, policy);
                    nanos[repeat] = System.nanoTime() - start;
                }
                java.util.Arrays.sort(nanos);
                var actual = JpegXlCodecs.decode(encoded.samples()).samples();
                if (actual.length != reference.length)
                    throw new AssertionError("Sample count differs");
                double sum = 0;
                for (int i = 0; i < actual.length; i++) {
                    var difference = (actual[i] & 255) - (reference[i] & 255);
                    sum += (double) difference * difference;
                }
                var psnr =
                        sum == 0
                                ? Double.POSITIVE_INFINITY
                                : 10 * Math.log10(65025.0 / (sum / actual.length));
                var sourcePdf = JpegXlOptimizerTest.pdf(input, false);
                var optimizedPdf =
                        org.skaldpdf.optimize.PdfOptimizer.recompress(
                                sourcePdf,
                                JpegXlOptimizerTest.options(),
                                new JpegXlImageRecompressor(policy, policy));
                csv.append(
                        String.format(
                                Locale.ROOT,
                                "%d,%d,%d,%s,%d,%.2f,%d,%.3f,%.3f,%.3f,%d,%d%n",
                                sample,
                                input.width(),
                                input.height(),
                                input.jpeg(),
                                input.samples().length,
                                policy.distance(),
                                encoded.samples().length,
                                100.0
                                        * (input.samples().length - encoded.samples().length)
                                        / input.samples().length,
                                nanos[1] / 1e6,
                                psnr,
                                sourcePdf.length,
                                optimizedPdf.length));
            }
        }
        Files.createDirectories(Path.of("build/benchmarks"));
        Files.writeString(Path.of("build/benchmarks/jpeg-xl-images.csv"), csv);
        System.out.print(csv);
    }
}
