package org.skaldpdf;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import org.skaldpdf.labels.ProductSticker;
import org.skaldpdf.image.ImageDataFactory;
import org.skaldpdf.layout.element.Image;
import org.skaldpdf.layout.element.Paragraph;
import org.skaldpdf.layout.properties.TextAlignment;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductStickerTest {
    static final ProductSticker.Spec SOJA_BA_L = new ProductSticker.Spec(
        "SOJA-BA-L",
        "CN",
        "Softy Jacket",
        "L",
        "",
        "80%Nylon, 20%Lycra",
        "8123613319580",
        "Orchid"
    );

    @Test
    void matchesEcomtoolsStickerInformationAndDecodes() throws Exception {
        var bytes = ProductSticker.pdf(SOJA_BA_L);
        try (var parsed = PdfTestSupport.load(bytes)) {
            assertEquals(1, parsed.getNumberOfPages());
            var page = parsed.getPage(0);
            assertEquals(ProductSticker.PAGE_SIZE.getWidth(), page.getMediaBox().getWidth(), 0.05);
            assertEquals(ProductSticker.PAGE_SIZE.getHeight(), page.getMediaBox().getHeight(), 0.05);
        }
        var text = PdfTestSupport.text(bytes).replaceAll("\\s+", " ");
        assertTrue(text.contains("Made in: CN"));
        assertTrue(text.contains("SKU: SOJA-BA-L"));
        assertTrue(text.contains("Size: L"));
        assertTrue(text.contains("Orchid Softy Jacket"));
        assertTrue(text.contains("Composition: 80%Nylon, 20%Lycra"));
        assertTrue(text.contains("8"));
        assertTrue(text.contains("123613") || text.contains("1 2 3 6 1 3"));
        assertEquals("8123613319580", decode(bytes));
        assertTrue(bytes.length < 28_000, "compact subset sticker should stay small, was " + bytes.length);
        PdfTestSupport.saveArtifacts("ecomtools-soja-ba-l", bytes);
    }

    @Test
    void keepsLengthAndLongComposition() throws Exception {
        var bytes = ProductSticker.pdf(new ProductSticker.Spec(
            "SOJA-BA-L",
            "CN",
            "Softy Jacket",
            "L",
            "72 cm",
            "Shell: 80% Nylon 20% Lycra Lining: 100% Polyester Padding: 100% Recycled polyester",
            "8123613319580",
            "Orchid"
        ));
        var text = PdfTestSupport.text(bytes);
        assertTrue(text.contains("Length: 72 cm"));
        assertTrue(text.contains("Composition:"));
        assertTrue(text.contains("Nylon") || text.contains("Shell"));
        assertEquals("8123613319580", decode(bytes));
        PdfTestSupport.saveArtifacts("ecomtools-soja-ba-l-long", bytes);
    }

    @Test
    void fileNameMatchesEcomtoolsZipEntry() {
        assertEquals("SOJA-BA-L_8123613319580_ean_sticker.pdf", ProductSticker.fileName(SOJA_BA_L));
    }

    @Test
    void tilesAnA4PrintSheet() throws Exception {
        var specs = java.util.List.of(SOJA_BA_L, SOJA_BA_L, SOJA_BA_L, SOJA_BA_L, SOJA_BA_L);
        var bytes = ProductSticker.sheet(specs);
        try (var parsed = PdfTestSupport.load(bytes)) {
            assertEquals(1, parsed.getNumberOfPages());
            var box = parsed.getPage(0).getMediaBox();
            assertEquals(org.skaldpdf.geom.PageSize.A4.getWidth(), box.getWidth(), 0.05);
            assertEquals(org.skaldpdf.geom.PageSize.A4.getHeight(), box.getHeight(), 0.05);
        }
        var text = PdfTestSupport.text(bytes);
        assertEquals(5, text.split("SOJA-BA-L", -1).length - 1);
        PdfTestSupport.saveArtifacts("sticker-sheet", bytes);
    }

    static String decode(byte[] pdf) throws Exception {
        var rendered = PdfTestSupport.renderFirstPage(pdf);
        var pixels = rendered.getRGB(0, 0, rendered.getWidth(), rendered.getHeight(), null, 0, rendered.getWidth());
        var source = new RGBLuminanceSource(rendered.getWidth(), rendered.getHeight(), pixels);
        var hints = Map.of(DecodeHintType.POSSIBLE_FORMATS, java.util.List.of(BarcodeFormat.EAN_13));
        return new MultiFormatReader().decode(new BinaryBitmap(new HybridBinarizer(source)), hints).getText();
    }

    static void writeGallery(Path directory) throws Exception {
        Files.createDirectories(directory);
        var originals = Path.of(System.getProperty("user.home"), "Downloads", "barcodes");
        if (Files.isDirectory(originals)) {
            try (var files = Files.list(originals)) {
                for (var original : files.filter(path -> path.getFileName().toString().endsWith("_ean_sticker.pdf")).toList()) {
                    var spec = specFromOriginal(original);
                    ProductSticker.write(directory.resolve(ProductSticker.fileName(spec)), spec);
                }
            }
        }
        ProductSticker.write(directory.resolve(ProductSticker.fileName(SOJA_BA_L)), SOJA_BA_L);
        ProductSticker.write(directory.resolve("SOJA-BA-L_8123613319580_with_length.pdf"), new ProductSticker.Spec(
            "SOJA-BA-L", "CN", "Softy Jacket", "L", "72 cm", "80%Nylon, 20%Lycra", "8123613319580", "Orchid"
        ));
        ProductSticker.write(directory.resolve("SOJA-BA-L_8123613319580_long_composition.pdf"), new ProductSticker.Spec(
            "SOJA-BA-L",
            "CN",
            "Softy Jacket",
            "L",
            "",
            "Shell: 80% Nylon 20% Lycra Lining: 100% Polyester Padding: 100% Recycled polyester",
            "8123613319580",
            "Orchid"
        ));
        var original = Path.of(System.getProperty("user.home"), "Downloads", "barcodes",
            "SOJA-BA-L_8123613319580_ean_sticker.pdf");
        if (Files.exists(original)) {
            writeComparison(directory.resolve("00-compare-SOJA-BA-L-original-vs-skald.pdf"),
                Files.readAllBytes(original), ProductSticker.pdf(SOJA_BA_L));
        }
    }

    private static void writeComparison(Path path, byte[] original, byte[] skald) throws Exception {
        Pdf.write(path, org.skaldpdf.geom.PageSize.A5.landscape(), org.skaldpdf.pdf.WriterProperties.defaults(),
            document -> {
                document.setMargins(28, 28, 28, 28);
                document.add(new Paragraph("ecomtools original vs Skald").bold().setFontSize(16));
                try {
                    document.add(new Paragraph("Original iText 93x35 mm sticker").setFontSize(9).setMarginTop(10));
                    document.add(new Image(ImageDataFactory.create(png(PdfTestSupport.renderFirstPage(original))))
                        .scaleToFit(340, 118));
                    document.add(new Paragraph("Skald ProductSticker (PDF 2.0, embedded font, same fields)")
                        .setFontSize(9).setMarginTop(10));
                    document.add(new Image(ImageDataFactory.create(png(PdfTestSupport.renderFirstPage(skald))))
                        .scaleToFit(340, 118));
                } catch (Exception exception) {
                    throw new IllegalStateException(exception);
                }
                document.add(new Paragraph("Both decode as EAN-13 8123613319580.")
                    .setFontSize(9).setTextAlignment(TextAlignment.LEFT).setMarginTop(12));
            });
    }

    private static byte[] png(BufferedImage image) throws Exception {
        var output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }

    private static ProductSticker.Spec specFromOriginal(Path original) throws Exception {
        var name = original.getFileName().toString().replace("_ean_sticker.pdf", "");
        var split = name.lastIndexOf('_');
        var sku = name.substring(0, split);
        var ean = name.substring(split + 1);
        var text = PdfTestSupport.text(Files.readAllBytes(original));
        var origin = capture(text, "Made in:");
        var size = capture(text, "Size:");
        var composition = capture(text, "Composition:");
        var titleLine = titleLine(text);
        var color = "";
        var title = titleLine;
        var firstSpace = titleLine.indexOf(' ');
        if (firstSpace > 0) {
            color = titleLine.substring(0, firstSpace);
            title = titleLine.substring(firstSpace + 1);
        }
        return new ProductSticker.Spec(sku, origin, title, size, "", composition, ean, color);
    }

    private static String capture(String text, String label) {
        for (var line : text.split("\\R")) {
            var trimmed = line.strip();
            if (trimmed.startsWith(label)) {
                return trimmed.substring(label.length()).strip();
            }
        }
        return "";
    }

    private static String titleLine(String text) {
        String previous = "";
        for (var line : text.split("\\R")) {
            var trimmed = line.strip();
            if (trimmed.startsWith("Composition:")) {
                return previous;
            }
            if (!trimmed.isBlank() && !trimmed.startsWith("Made in:") && !trimmed.startsWith("SKU:")
                && !trimmed.startsWith("Size:") && !trimmed.startsWith("Length:")
                && !trimmed.matches("[0-9 ]+")) {
                previous = trimmed;
            }
        }
        return previous;
    }
}
