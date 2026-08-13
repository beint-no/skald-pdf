package org.skaldpdf;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import org.skaldpdf.barcode.Ean13Barcode;
import org.skaldpdf.geom.PageSize;
import org.skaldpdf.layout.Document;
import org.skaldpdf.layout.element.Image;
import org.skaldpdf.pdf.PdfDocument;
import org.skaldpdf.pdf.PdfWriter;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class Ean13BarcodeTest {
    @Test
    void calculatesAndValidatesChecksum() {
        assertEquals(7, Ean13Barcode.checkDigit("590123412345"));
        var barcode = new Ean13Barcode("590123412345");
        assertEquals("5901234123457", barcode.value());
        assertEquals(95, barcode.encodedModules().length);
        assertEquals(113 * barcode.moduleWidth(), barcode.intrinsicWidth());
        assertThrows(IllegalArgumentException.class, () -> new Ean13Barcode("5901234123458"));
    }

    @Test
    void renderedLabelIsMachineReadable() throws Exception {
        var output = new ByteArrayOutputStream();
        var pdf = new PdfDocument(new PdfWriter(output));
        var barcode = new Ean13Barcode("5901234123457")
            .withBarHeight(48f)
            .withFontSize(9f)
            .withModuleWidth(1.4f);
        var image = new Image(barcode).scaleToFit(260, 110);
        var document = new Document(pdf, new PageSize(320, 150));
        document.setMargins(20, 20, 20, 20);
        document.add(image);
        document.close();

        var rendered = PdfTestSupport.renderFirstPage(output.toByteArray());
        var pixels = rendered.getRGB(0, 0, rendered.getWidth(), rendered.getHeight(), null, 0, rendered.getWidth());
        var source = new RGBLuminanceSource(rendered.getWidth(), rendered.getHeight(), pixels);
        var result = new MultiFormatReader().decode(new BinaryBitmap(new HybridBinarizer(source)));
        assertEquals("5901234123457", result.getText());
        PdfTestSupport.saveArtifacts("ean13-label", output.toByteArray());
    }
}
