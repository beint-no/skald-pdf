package org.skaldpdf.optimize.jpegli;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.skaldpdf.optimize.OptimizeOptions;
import org.skaldpdf.optimize.PdfOptimizer;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.FileVisitOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Opt-in rendered-page comparison for a private PDF corpus. */
class PdfVisualCorpusTest {
    private static final float RENDER_DPI = 96;

    @Test
    void renderedPagesRemainPerceptuallyEquivalent() throws Exception {
        var configured = System.getenv("SKALD_PDF_VISUAL_CORPUS");
        Assumptions.assumeTrue(configured != null && !configured.isBlank(),
            "Set SKALD_PDF_VISUAL_CORPUS to run rendered corpus validation");
        var corpus = Path.of(configured);
        Assumptions.assumeTrue(Files.isDirectory(corpus), "Visual corpus directory does not exist");
        var paths = new ArrayList<Path>();
        try (var files = Files.walk(corpus, FileVisitOption.FOLLOW_LINKS)) {
            files.filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".pdf"))
                .sorted().forEach(paths::add);
        }
        Assumptions.assumeFalse(paths.isEmpty(), "Visual corpus has no PDFs");

        var options = OptimizeOptions.attachments();
        var recompressor = new JpegliImageRecompressor();
        var csv = new StringBuilder("file,page,psnr_db,mean_ssim\n");
        var rendered = 0;
        var changed = 0;
        var minimumPsnr = Double.POSITIVE_INFINITY;
        var minimumSsim = Double.POSITIVE_INFINITY;
        var worstFile = "";
        var worstPage = 0;
        BufferedImage worstBefore = null;
        BufferedImage worstAfter = null;
        for (var path : paths) {
            var source = Files.readAllBytes(path);
            var optimized = PdfOptimizer.recompress(source, options, recompressor);
            if (optimized == source) {
                continue;
            }
            changed++;
            try (var before = Loader.loadPDF(source); var after = Loader.loadPDF(optimized)) {
                assertEquals(before.getNumberOfPages(), after.getNumberOfPages(), path.toString());
                var beforeRenderer = new PDFRenderer(before);
                var afterRenderer = new PDFRenderer(after);
                for (var page : sampledPages(before.getNumberOfPages())) {
                    var first = beforeRenderer.renderImageWithDPI(page, RENDER_DPI, ImageType.RGB);
                    var second = afterRenderer.renderImageWithDPI(page, RENDER_DPI, ImageType.RGB);
                    var metrics = compare(first, second);
                    minimumPsnr = Math.min(minimumPsnr, metrics.psnr());
                    if (metrics.meanSsim() < minimumSsim) {
                        minimumSsim = metrics.meanSsim();
                        worstFile = path.getFileName().toString();
                        worstPage = page + 1;
                        worstBefore = first;
                        worstAfter = second;
                    }
                    rendered++;
                    csv.append(csv(path.getFileName().toString())).append(',').append(page + 1).append(',')
                        .append(String.format(Locale.ROOT, "%.3f,%.6f%n", metrics.psnr(), metrics.meanSsim()));
                }
            }
        }
        var output = Path.of("skald-optimize-jpegli", "build", "benchmarks");
        Files.createDirectories(output);
        Files.writeString(output.resolve("private-pdf-visual.csv"), csv);
        Files.writeString(output.resolve("private-pdf-visual.md"), String.format(Locale.ROOT, """
            # Private PDF rendered comparison

            | Metric | Result |
            | --- | ---: |
            | Changed PDFs | %,d |
            | Pages rendered twice | %,d |
            | Minimum PSNR | %.3f dB |
            | Minimum mean SSIM | %.6f |
            | Worst rendered page | %s page %d |
            """, changed, rendered, minimumPsnr, minimumSsim, worstFile, worstPage));
        if (worstBefore != null && worstAfter != null) {
            ImageIO.write(worstBefore, "png", output.resolve("private-pdf-visual-worst-before.png").toFile());
            ImageIO.write(worstAfter, "png", output.resolve("private-pdf-visual-worst-after.png").toFile());
            ImageIO.write(difference(worstBefore, worstAfter), "png",
                output.resolve("private-pdf-visual-worst-difference.png").toFile());
        }
        assertTrue(minimumPsnr >= 25, "Minimum corpus PSNR=" + minimumPsnr);
        assertTrue(minimumSsim >= 0.88, "Minimum corpus SSIM=" + minimumSsim);
    }

    private static LinkedHashSet<Integer> sampledPages(int pages) {
        var result = new LinkedHashSet<Integer>();
        result.add(0);
        result.add(pages / 2);
        result.add(pages - 1);
        return result;
    }

    private static Metrics compare(BufferedImage first, BufferedImage second) {
        assertEquals(first.getWidth(), second.getWidth());
        assertEquals(first.getHeight(), second.getHeight());
        var width = first.getWidth();
        var height = first.getHeight();
        var firstPixels = first.getRGB(0, 0, width, height, null, 0, width);
        var secondPixels = second.getRGB(0, 0, width, height, null, 0, width);
        double squaredError = 0;
        for (int index = 0; index < firstPixels.length; index++) {
            for (int shift = 0; shift <= 16; shift += 8) {
                var difference = (firstPixels[index] >>> shift & 0xff) - (secondPixels[index] >>> shift & 0xff);
                squaredError += difference * difference;
            }
        }
        var mse = squaredError / (firstPixels.length * 3.0);
        var psnr = mse == 0 ? 99 : 10 * Math.log10(255 * 255 / mse);
        return new Metrics(psnr, meanSsim(firstPixels, secondPixels, width, height));
    }

    private static BufferedImage difference(BufferedImage first, BufferedImage second) {
        var width = first.getWidth();
        var height = first.getHeight();
        var left = first.getRGB(0, 0, width, height, null, 0, width);
        var right = second.getRGB(0, 0, width, height, null, 0, width);
        var difference = new int[left.length];
        for (int index = 0; index < left.length; index++) {
            var red = Math.min(255, Math.abs((left[index] >>> 16 & 0xff) - (right[index] >>> 16 & 0xff)) * 4);
            var green = Math.min(255, Math.abs((left[index] >>> 8 & 0xff) - (right[index] >>> 8 & 0xff)) * 4);
            var blue = Math.min(255, Math.abs((left[index] & 0xff) - (right[index] & 0xff)) * 4);
            difference[index] = 0xff00_0000 | red << 16 | green << 8 | blue;
        }
        var result = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        result.setRGB(0, 0, width, height, difference, 0, width);
        return result;
    }

    private static double meanSsim(int[] first, int[] second, int width, int height) {
        var total = 0.0;
        var blocks = 0;
        for (int top = 0; top < height; top += 8) {
            for (int left = 0; left < width; left += 8) {
                var bottom = Math.min(height, top + 8);
                var right = Math.min(width, left + 8);
                var count = (bottom - top) * (right - left);
                double firstMean = 0;
                double secondMean = 0;
                for (int y = top; y < bottom; y++) {
                    for (int x = left; x < right; x++) {
                        firstMean += luminance(first[y * width + x]);
                        secondMean += luminance(second[y * width + x]);
                    }
                }
                firstMean /= count;
                secondMean /= count;
                double firstVariance = 0;
                double secondVariance = 0;
                double covariance = 0;
                for (int y = top; y < bottom; y++) {
                    for (int x = left; x < right; x++) {
                        var firstDelta = luminance(first[y * width + x]) - firstMean;
                        var secondDelta = luminance(second[y * width + x]) - secondMean;
                        firstVariance += firstDelta * firstDelta;
                        secondVariance += secondDelta * secondDelta;
                        covariance += firstDelta * secondDelta;
                    }
                }
                var denominator = Math.max(1, count - 1);
                firstVariance /= denominator;
                secondVariance /= denominator;
                covariance /= denominator;
                var c1 = 6.5025;
                var c2 = 58.5225;
                total += (2 * firstMean * secondMean + c1) * (2 * covariance + c2)
                    / ((firstMean * firstMean + secondMean * secondMean + c1)
                    * (firstVariance + secondVariance + c2));
                blocks++;
            }
        }
        return total / blocks;
    }

    private static int luminance(int rgb) {
        return (77 * (rgb >>> 16 & 0xff) + 150 * (rgb >>> 8 & 0xff) + 29 * (rgb & 0xff)) >>> 8;
    }

    private static String csv(String value) {
        return '"' + value.replace("\"", "\"\"") + '"';
    }

    private record Metrics(double psnr, double meanSsim) {
    }
}
