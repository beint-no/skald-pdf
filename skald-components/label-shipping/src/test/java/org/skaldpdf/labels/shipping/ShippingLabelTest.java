package org.skaldpdf.labels.shipping;

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
import org.skaldpdf.Pdf;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShippingLabelTest {
    @Test
    void writesA100By150MmLabelThatDecodes() throws Exception {
        var spec = new ShippingLabel.Spec(
            new ShippingLabel.Address("Nordlys Handel AS", "Storgata 10", "0184 OSLO"),
            new ShippingLabel.Address("Fjordbutikken AS", "Kaien 4", "5003 BERGEN"),
            "373724189NO",
            "Posten Bedriftspakke",
            "PO-5512",
            "https://sporing.posten.no/373724189NO"
        );
        var bytes = ShippingLabel.pdf(spec);
        assertTrue(new String(bytes, 0, 8, StandardCharsets.US_ASCII).startsWith("%PDF-2.0"));
        try (var document = Loader.loadPDF(bytes)) {
            var box = document.getPage(0).getMediaBox();
            assertEquals(ShippingLabel.PAGE_SIZE.getWidth(), box.getWidth(), 0.05);
            assertEquals(ShippingLabel.PAGE_SIZE.getHeight(), box.getHeight(), 0.05);
        }
        var text = Pdf.extractText(bytes);
        assertTrue(text.contains("Fjordbutikken AS"));
        assertTrue(text.contains("5003 BERGEN"));
        assertTrue(text.contains("373724189NO"));
        assertEquals("https://sporing.posten.no/373724189NO", decodeQr(bytes));
    }

    private static String decodeQr(byte[] pdf) throws Exception {
        try (var document = Loader.loadPDF(pdf)) {
            var image = new PDFRenderer(document).renderImageWithDPI(0, 180, ImageType.RGB);
            var pixels = image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
            var source = new RGBLuminanceSource(image.getWidth(), image.getHeight(), pixels);
            var hints = Map.of(DecodeHintType.POSSIBLE_FORMATS, List.of(BarcodeFormat.QR_CODE));
            return new MultiFormatReader().decode(new BinaryBitmap(new HybridBinarizer(source)), hints).getText();
        }
    }
}
