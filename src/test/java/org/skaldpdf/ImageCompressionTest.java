package org.skaldpdf;

import org.skaldpdf.geom.PageSize;
import org.skaldpdf.image.ImageDataFactory;
import org.skaldpdf.layout.Document;
import org.skaldpdf.layout.element.AreaBreak;
import org.skaldpdf.layout.element.Image;
import org.skaldpdf.pdf.PdfDocument;
import org.skaldpdf.pdf.PdfWriter;
import org.skaldpdf.pdf.Compression;
import org.skaldpdf.pdf.WriterProperties;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Collections;
import java.util.IdentityHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImageCompressionTest {
    @Test
    void appliesAdaptivePredictionAndPreservesTransparency() throws Exception {
        var image = ImageDataFactory.create(transparentGradient());
        var compressed = render(image, Compression.MAXIMUM, 1);
        var stored = render(image, Compression.NONE, 1);

        assertTrue(compressed.length < stored.length / 3,
            () -> "Predicted Flate image should compress well: " + compressed.length + " vs " + stored.length);
        var rendered = PdfTestSupport.renderFirstPage(compressed);
        var center = new Color(rendered.getRGB(432, 432));
        assertTrue(center.getRed() > center.getBlue(), "Transparent red gradient should render over white");
    }

    @Test
    void sharesOneImageObjectAcrossPages() throws Exception {
        var image = ImageDataFactory.create(transparentGradient());
        var bytes = render(image, Compression.BALANCED, 4);

        try (var document = PdfTestSupport.load(bytes)) {
            var identities = Collections.newSetFromMap(new IdentityHashMap<org.apache.pdfbox.cos.COSBase, Boolean>());
            for (var page : document.getPages()) {
                var resources = page.getResources();
                for (var name : resources.getXObjectNames()) {
                    identities.add(resources.getXObject(name).getCOSObject());
                }
            }
            assertEquals(1, identities.size());
        }
    }

    private static byte[] render(org.skaldpdf.image.ImageData image, Compression compression, int pages) {
        var output = new ByteArrayOutputStream();
        var properties = new WriterProperties(compression);
        try (var pdf = new PdfDocument(new PdfWriter(output, properties));
             var document = new Document(pdf, PageSize.A4)) {
            for (int page = 0; page < pages; page++) {
                if (page > 0) {
                    document.add(new AreaBreak());
                }
                document.add(new Image(image).scaleToFit(360, 360));
            }
        }
        return output.toByteArray();
    }

    private static byte[] transparentGradient() throws Exception {
        var image = new BufferedImage(512, 512, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                var alpha = x * 255 / (image.getWidth() - 1);
                image.setRGB(x, y, new Color(220, y * 80 / image.getHeight(), 40, alpha).getRGB());
            }
        }
        var output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }
}
