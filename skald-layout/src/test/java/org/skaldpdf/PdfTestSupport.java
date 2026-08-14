package org.skaldpdf;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

public final class PdfTestSupport {
    private static final Path OUTPUT_DIRECTORY = Path.of("build", "use-case-pdfs");

    private PdfTestSupport() {
    }

    public static PDDocument load(byte[] bytes) throws IOException {
        assertTrue(bytes.length > 200, "PDF should contain more than an empty shell");
        assertTrue(new String(bytes, 0, 5, java.nio.charset.StandardCharsets.US_ASCII).startsWith("%PDF-"));
        return Loader.loadPDF(bytes);
    }

    public static String text(byte[] bytes) throws IOException {
        try (var document = load(bytes)) {
            return new PDFTextStripper().getText(document);
        }
    }

    public static BufferedImage renderFirstPage(byte[] bytes) throws IOException {
        try (var document = load(bytes)) {
            return new PDFRenderer(document).renderImageWithDPI(0, 144, ImageType.RGB);
        }
    }

    public static void assertNoHeavyHorizontalBars(BufferedImage image) {
        var width = image.getWidth();
        var height = image.getHeight();
        var darkRows = 0;
        var longestDarkRun = 0;
        var run = 0;
        for (int y = 0; y < height; y++) {
            var dark = 0;
            for (int x = 0; x < width; x += 2) {
                var rgb = image.getRGB(x, y) & 0x00ff_ffff;
                var red = (rgb >> 16) & 0xff;
                var green = (rgb >> 8) & 0xff;
                var blue = rgb & 0xff;
                if (red < 40 && green < 40 && blue < 40) {
                    dark++;
                }
            }
            var ratio = dark / (width / 2.0);
            if (ratio > 0.45) {
                darkRows++;
                run++;
                longestDarkRun = Math.max(longestDarkRun, run);
            } else {
                run = 0;
            }
        }
        assertTrue(longestDarkRun < 8,
            "A horizontal rule should be a hairline, not a bar through text. dark-run="
                + longestDarkRun + " dark-rows=" + darkRows);
    }

    public static String visualFingerprint(BufferedImage image) {
        final int cells = 16;
        var hex = new StringBuilder(cells * cells * 2);
        for (int cellY = 0; cellY < cells; cellY++) {
            var y0 = cellY * image.getHeight() / cells;
            var y1 = (cellY + 1) * image.getHeight() / cells;
            for (int cellX = 0; cellX < cells; cellX++) {
                var x0 = cellX * image.getWidth() / cells;
                var x1 = (cellX + 1) * image.getWidth() / cells;
                var sum = 0L;
                var count = 0;
                for (int y = y0; y < y1; y++) {
                    for (int x = x0; x < x1; x++) {
                        var rgb = image.getRGB(x, y);
                        var red = (rgb >> 16) & 0xff;
                        var green = (rgb >> 8) & 0xff;
                        var blue = rgb & 0xff;
                        sum += (red * 299L + green * 587L + blue * 114L) / 1000L;
                        count++;
                    }
                }
                hex.append(String.format("%02x", count == 0 ? 0 : (int) (sum / count)));
            }
        }
        return hex.toString();
    }

    public static int fingerprintDistance(String first, String second) {
        if (first.length() != second.length() || first.length() % 2 != 0) {
            throw new IllegalArgumentException("Fingerprints must be equal-length hex");
        }
        var distance = 0;
        for (int index = 0; index < first.length(); index += 2) {
            var a = Integer.parseInt(first.substring(index, index + 2), 16);
            var b = Integer.parseInt(second.substring(index, index + 2), 16);
            distance += Math.abs(a - b);
        }
        return distance;
    }

    public static void assertVisibleInk(BufferedImage image) {
        var nonWhitePixels = 0L;
        var totalPixels = (long) image.getWidth() * image.getHeight();
        for (int y = 0; y < image.getHeight(); y += 2) {
            for (int x = 0; x < image.getWidth(); x += 2) {
                var rgb = image.getRGB(x, y) & 0x00ff_ffff;
                if (rgb != 0x00ff_ffff) {
                    nonWhitePixels++;
                }
            }
        }
        var sampledPixels = totalPixels / 4.0;
        var ratio = nonWhitePixels / sampledPixels;
        assertTrue(ratio > 0.002, "Rendered page should contain visible content, ratio=" + ratio);
        assertTrue(ratio < 0.70, "Rendered page should not be accidentally filled, ratio=" + ratio);
    }

    public static void saveArtifacts(String name, byte[] bytes) throws IOException {
        Files.createDirectories(OUTPUT_DIRECTORY);
        Files.write(OUTPUT_DIRECTORY.resolve(name + ".pdf"), bytes);
        try (var document = load(bytes)) {
            var renderer = new PDFRenderer(document);
            for (int page = 0; page < Math.min(document.getNumberOfPages(), 3); page++) {
                var image = renderer.renderImageWithDPI(page, 108, ImageType.RGB);
                ImageIO.write(image, "png", OUTPUT_DIRECTORY.resolve(name + "-" + (page + 1) + ".png").toFile());
            }
        }
    }

    public static byte[] sampleLogo() throws IOException {
        var image = new BufferedImage(240, 80, BufferedImage.TYPE_INT_RGB);
        var graphics = image.createGraphics();
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
        graphics.setColor(new Color(24, 83, 63));
        graphics.fillRoundRect(4, 4, 232, 72, 16, 16);
        graphics.setColor(Color.WHITE);
        graphics.setFont(new java.awt.Font(java.awt.Font.SANS_SERIF, java.awt.Font.BOLD, 34));
        graphics.drawString("SKALD", 50, 53);
        graphics.dispose();
        var output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }
}
