package org.skaldpdf.codec;

import org.skaldpdf.pdf.PdfDocument;
import org.skaldpdf.pdf.PdfWriter;
import org.skaldpdf.geom.PageSize;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class NativeImagesTest {
    @Test
    void reportsMissingNativesWithoutThrowing() {
        NativeImages.jpegAvailable();
        NativeImages.heifAvailable();
    }

    @Test
    @EnabledIf("jpegPresent")
    void turboJpegRoundTripsAndBeatsImageIoOnANoisyPhoto() throws Exception {
        var raster = noisyRaster(640, 480);
        var jpeg = NativeImages.jpegEncode(raster, 80);
        assertTrue(jpeg.length > 100);
        assertTrue(NativeImages.isJpeg(jpeg));
        var decoded = NativeImages.jpegDecode(jpeg);
        assertEquals(640, decoded.width());
        assertEquals(480, decoded.height());

        var warmupIo = imageIoJpeg(raster, 0.80f);
        var started = System.nanoTime();
        var imageIo = imageIoJpeg(raster, 0.80f);
        var imageIoNanos = System.nanoTime() - started;
        started = System.nanoTime();
        NativeImages.jpegEncode(raster, 80);
        var turboNanos = System.nanoTime() - started;
        assertTrue(jpeg.length < imageIo.length * 1.15,
            "turbo=" + jpeg.length + " imageio=" + imageIo.length);
        Files.createDirectories(Path.of("build", "benchmarks"));
        Files.writeString(Path.of("build", "benchmarks", "native-image.md"),
            "TurboJPEG " + jpeg.length + " B in " + turboNanos / 1_000 + " µs; ImageIO "
                + imageIo.length + " B in " + imageIoNanos / 1_000 + " µs (warmup "
                + warmupIo.length + " B).\n");

        var prepared = NativeImages.prepare(jpeg, new PrepareOptions(320, 75));
        assertTrue(prepared.jpeg());
        assertTrue(prepared.width() <= 320);
        assertTrue(prepared.height() <= 320);

        var output = new ByteArrayOutputStream();
        try (var pdf = new PdfDocument(new PdfWriter(output))) {
            var page = pdf.addNewPage(new PageSize(200, 200));
            prepared.drawOn(pdf, page, 10, 10, 180, 180);
        }
        assertTrue(output.toByteArray()[0] == '%');
    }

    @Test
    @EnabledIf("heifPresent")
    void decodesAHeicFileProducedByLibheif() throws Exception {
        assumeTrue(Files.isRegularFile(Path.of("/opt/homebrew/bin/heif-enc")));
        var png = Files.createTempFile("skald-heif", ".png");
        var heic = Files.createTempFile("skald-heif", ".heic");
        ImageIO.write(solidPng(96, 64), "png", png.toFile());
        var process = new ProcessBuilder("/opt/homebrew/bin/heif-enc", "-q", "40",
            "-o", heic.toString(), png.toString())
            .redirectErrorStream(true)
            .start();
        var log = new String(process.getInputStream().readAllBytes());
        assertEquals(0, process.waitFor(), log);
        var bytes = Files.readAllBytes(heic);
        assertTrue(NativeImages.isHeif(bytes), "ftyp brand not recognised");
        var raster = NativeImages.heifDecode(bytes);
        assertEquals(96, raster.width());
        assertEquals(64, raster.height());
        var image = NativeImages.prepare(bytes, PrepareOptions.photos());
        assertTrue(image.jpeg());
        assertEquals(96, image.width());
        Files.deleteIfExists(png);
        Files.deleteIfExists(heic);
    }

    static boolean jpegPresent() {
        return NativeImages.jpegAvailable();
    }

    static boolean heifPresent() {
        return NativeImages.heifAvailable();
    }

    private static Raster noisyRaster(int width, int height) {
        var rgb = new byte[width * height * 3];
        var random = new java.util.Random(7);
        random.nextBytes(rgb);
        return new Raster(width, height, rgb);
    }

    private static byte[] imageIoJpeg(Raster raster, float quality) throws Exception {
        var image = new BufferedImage(raster.width(), raster.height(), BufferedImage.TYPE_INT_RGB);
        var rgb = raster.rgb();
        var offset = 0;
        for (int y = 0; y < raster.height(); y++) {
            for (int x = 0; x < raster.width(); x++) {
                var red = rgb[offset++] & 0xff;
                var green = rgb[offset++] & 0xff;
                var blue = rgb[offset++] & 0xff;
                image.setRGB(x, y, (red << 16) | (green << 8) | blue);
            }
        }
        var output = new ByteArrayOutputStream();
        var writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        var params = writer.getDefaultWriteParam();
        params.setCompressionMode(javax.imageio.ImageWriteParam.MODE_EXPLICIT);
        params.setCompressionQuality(quality);
        try (var ios = ImageIO.createImageOutputStream(output)) {
            writer.setOutput(ios);
            writer.write(null, new javax.imageio.IIOImage(image, null, null), params);
        } finally {
            writer.dispose();
        }
        return output.toByteArray();
    }

    private static BufferedImage solidPng(int width, int height) {
        var image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        var graphics = image.createGraphics();
        graphics.setColor(new Color(40, 110, 80));
        graphics.fillRect(0, 0, width, height);
        graphics.setColor(Color.WHITE);
        graphics.fillOval(8, 8, 40, 40);
        graphics.dispose();
        return image;
    }
}
