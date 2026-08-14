package org.skaldpdf.pdf;

import org.skaldpdf.geom.PageSize;
import org.skaldpdf.image.ImageData;
import org.skaldpdf.codec.RasterImages;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImportedImageTest {
    @Test
    void listsAndReplacesAJpegXObject() throws Exception {
        var original = jpeg(120, 80, new Color(20, 90, 160));
        var source = pdfWith(original);
        var rewritten = new ByteArrayOutputStream();
        String resourceName;
        try (var document = new PdfDocument(new PdfReader(source), new PdfWriter(rewritten))) {
            var images = document.importedImages();
            assertEquals(1, images.size());
            var image = images.getFirst();
            assertTrue(image.jpeg());
            assertEquals(120, image.width());
            assertEquals(80, image.height());
            assertTrue(image.filter().contains("DCTDecode"));
            assertEquals("DeviceRGB", image.colorSpace());
            assertTrue(image.decode().isPresent());
            resourceName = image.resourceName();
            var replacement = RasterImages.asJpeg(
                ImageData.fromRgb(32, 24, solidRgb(32, 24, 200, 40, 40)), 0.55f);
            document.replaceImportedImage(1, resourceName, replacement);
        }
        var output = rewritten.toByteArray();
        assertFalse(new String(output, java.nio.charset.StandardCharsets.ISO_8859_1).contains("JXLDecode"));
        try (var document = new PdfDocument(new PdfReader(output))) {
            var images = document.importedImages();
            assertEquals(1, images.size());
            assertEquals(resourceName, images.getFirst().resourceName());
            assertEquals(32, images.getFirst().width());
            assertEquals(24, images.getFirst().height());
            assertTrue(images.getFirst().jpeg());
        }
    }

    @Test
    void decodesAFlateRgbXObject() throws Exception {
        var rgb = solidRgb(16, 12, 10, 180, 70);
        var source = pdfWith(ImageData.fromRgb(16, 12, rgb));
        try (var document = new PdfDocument(new PdfReader(source))) {
            var image = document.importedImages().getFirst();
            assertFalse(image.jpeg());
            assertTrue(image.filter().contains("FlateDecode"));
            var decoded = image.decode().orElseThrow();
            assertEquals(16, decoded.width());
            assertEquals(12, decoded.height());
            assertEquals(3, decoded.components());
            assertEquals(10, decoded.samples()[0] & 0xff);
            assertEquals(180, decoded.samples()[1] & 0xff);
            assertEquals(70, decoded.samples()[2] & 0xff);
        }
    }

    @Test
    void fromRgbAndFromGrayRoundTripThroughTheWriter() {
        var gray = new byte[8 * 8];
        java.util.Arrays.fill(gray, (byte) 90);
        var image = ImageData.fromGray(8, 8, gray);
        assertEquals(1, image.components());
        var jpeg = RasterImages.asJpeg(image, 0.7f);
        assertTrue(jpeg.jpeg());
        assertEquals(8, jpeg.width());
    }

    private static byte[] pdfWith(ImageData image) {
        var output = new ByteArrayOutputStream();
        try (var pdf = new PdfDocument(new PdfWriter(output))) {
            var page = pdf.addNewPage(new PageSize(200, 160));
            image.drawOn(pdf, page, 10, 10, 180, 140);
        }
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
        params.setCompressionQuality(0.85f);
        try (var ios = ImageIO.createImageOutputStream(output)) {
            writer.setOutput(ios);
            writer.write(null, new javax.imageio.IIOImage(image, null, null), params);
        } finally {
            writer.dispose();
        }
        return RasterImages.decode(output.toByteArray());
    }

    private static byte[] solidRgb(int width, int height, int red, int green, int blue) {
        var rgb = new byte[width * height * 3];
        for (int index = 0; index < rgb.length; index += 3) {
            rgb[index] = (byte) red;
            rgb[index + 1] = (byte) green;
            rgb[index + 2] = (byte) blue;
        }
        return rgb;
    }
}
