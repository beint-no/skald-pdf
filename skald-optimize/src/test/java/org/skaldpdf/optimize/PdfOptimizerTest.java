package org.skaldpdf.optimize;

import org.apache.pdfbox.Loader;
import org.skaldpdf.geom.PageSize;
import org.skaldpdf.image.ImageData;
import org.skaldpdf.image.ImageDataFactory;
import org.skaldpdf.pdf.PdfDocument;
import org.skaldpdf.pdf.PdfReader;
import org.skaldpdf.pdf.PdfWriter;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PdfOptimizerTest {
    @Test
    void recompressesALargePngPhotoIntoASmallerJpegXObject() throws Exception {
        var noisy = ImageDataFactory.create(noisyPng(640, 480));
        var source = pdfWith(noisy);
        var optimized = PdfOptimizer.recompress(source, new OptimizeOptions(320, 0.55f, true));

        assertTrue(optimized.length < source.length,
            "optimized=" + optimized.length + " source=" + source.length);
        assertTrue(new String(optimized, 0, 8, StandardCharsets.US_ASCII).startsWith("%PDF-2.0"));
        assertFalse(new String(optimized, StandardCharsets.ISO_8859_1).contains("/JXLDecode"));

        try (var document = new PdfDocument(new PdfReader(optimized))) {
            var images = document.importedImages();
            assertEquals(1, images.size());
            assertTrue(images.getFirst().jpeg());
            assertTrue(images.getFirst().width() <= 320);
            assertTrue(images.getFirst().height() <= 320);
        }
        try (var box = Loader.loadPDF(optimized)) {
            assertEquals(1, box.getNumberOfPages());
        }
    }

    @Test
    void leavesAnAlreadySmallJpegAloneWhenRecompressWouldGrowIt() throws Exception {
        var tiny = jpeg(40, 30, new Color(80, 20, 20));
        var source = pdfWith(tiny);
        var optimized = PdfOptimizer.recompress(source, new OptimizeOptions(1600, 0.95f, true));
        try (var document = new PdfDocument(new PdfReader(optimized))) {
            var image = document.importedImages().getFirst();
            assertTrue(image.jpeg());
            assertEquals(40, image.width());
            assertEquals(30, image.height());
        }
    }

    private static byte[] pdfWith(ImageData image) {
        var output = new ByteArrayOutputStream();
        try (var pdf = new PdfDocument(new PdfWriter(output))) {
            var page = pdf.addNewPage(new PageSize(400, 300));
            image.drawOn(pdf, page, 10, 10, 380, 280);
        }
        return output.toByteArray();
    }

    private static byte[] noisyPng(int width, int height) throws Exception {
        var image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        var random = new java.util.Random(11);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                image.setRGB(x, y, random.nextInt(0x0100_0000));
            }
        }
        var output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }

    private static ImageData jpeg(int width, int height, Color color) throws Exception {
        var image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        var graphics = image.createGraphics();
        graphics.setColor(color);
        graphics.fillRect(0, 0, width, height);
        graphics.dispose();
        var output = new ByteArrayOutputStream();
        var writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        var params = writer.getDefaultWriteParam();
        params.setCompressionMode(javax.imageio.ImageWriteParam.MODE_EXPLICIT);
        params.setCompressionQuality(0.4f);
        try (var ios = ImageIO.createImageOutputStream(output)) {
            writer.setOutput(ios);
            writer.write(null, new javax.imageio.IIOImage(image, null, null), params);
        } finally {
            writer.dispose();
        }
        return ImageDataFactory.create(output.toByteArray());
    }
}
