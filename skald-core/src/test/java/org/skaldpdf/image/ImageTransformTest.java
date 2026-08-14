package org.skaldpdf.image;

import org.junit.jupiter.api.Test;
import org.skaldpdf.codec.RasterImages;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ImageTransformTest {
    @Test
    void downscalesAndJpegCompressesAPhoto() throws Exception {
        var png = png(1200, 800);
        var original = RasterImages.decode(png);
        assertTrue(original.width() == 1200);
        var scaled = RasterImages.scaleToFit(original, 400, 300);
        assertTrue(scaled.width() <= 400);
        assertTrue(scaled.height() <= 300);
        var jpeg = RasterImages.asJpeg(original, 0.55f);
        assertTrue(jpeg.jpeg());
        var uncompressed = original.width() * original.height() * 3;
        assertTrue(jpeg.samples().length < uncompressed / 4,
            "JPEG should be far smaller than raw RGB, jpeg=" + jpeg.samples().length
                + " raw=" + uncompressed + " png=" + png.length);
    }

    private static byte[] png(int width, int height) throws Exception {
        var image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        var graphics = image.createGraphics();
        graphics.setColor(new Color(30, 90, 70));
        graphics.fillRect(0, 0, width, height);
        var random = new java.util.Random(1);
        for (int i = 0; i < 8_000; i++) {
            graphics.setColor(new Color(random.nextInt(256), random.nextInt(256), random.nextInt(256)));
            graphics.fillRect(random.nextInt(width), random.nextInt(height), 6, 6);
        }
        graphics.dispose();
        var output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }
}
