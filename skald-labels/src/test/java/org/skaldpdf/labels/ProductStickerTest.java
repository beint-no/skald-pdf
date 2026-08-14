package org.skaldpdf.labels;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductStickerTest {
    private static final ProductSticker.Spec SOJA = new ProductSticker.Spec(
        "SOJA-BA-L", "CN", "Softy Jacket", "L", "",
        "80%Nylon, 20%Lycra", "8123613319580", "Orchid"
    );

    @Test
    void writesA93By35MmStickerThatDecodes() throws Exception {
        var bytes = ProductSticker.pdf(SOJA);
        try (var document = Loader.loadPDF(bytes)) {
            var box = document.getPage(0).getMediaBox();
            assertEquals(ProductSticker.PAGE_SIZE.getWidth(), box.getWidth(), 0.05);
            assertEquals(ProductSticker.PAGE_SIZE.getHeight(), box.getHeight(), 0.05);
        }
        assertEquals("8123613319580", decode(bytes));
        assertEquals("SOJA-BA-L_8123613319580_ean_sticker.pdf", ProductSticker.fileName(SOJA));
        assertTrue(bytes.length < 28_000, "was " + bytes.length);
    }

    @Test
    void tilesAnA4Sheet() throws Exception {
        var bytes = ProductSticker.sheet(List.of(SOJA, SOJA, SOJA));
        try (var document = Loader.loadPDF(bytes)) {
            assertEquals(1, document.getNumberOfPages());
            var box = document.getPage(0).getMediaBox();
            assertEquals(org.skaldpdf.geom.PageSize.A4.getWidth(), box.getWidth(), 0.05);
        }
    }

    private static String decode(byte[] pdf) throws Exception {
        try (var document = Loader.loadPDF(pdf)) {
            var image = new PDFRenderer(document).renderImageWithDPI(0, 144, ImageType.RGB);
            var pixels = image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
            var source = new RGBLuminanceSource(image.getWidth(), image.getHeight(), pixels);
            var hints = Map.of(DecodeHintType.POSSIBLE_FORMATS, List.of(BarcodeFormat.EAN_13));
            return new MultiFormatReader().decode(new BinaryBitmap(new HybridBinarizer(source)), hints).getText();
        }
    }
}
