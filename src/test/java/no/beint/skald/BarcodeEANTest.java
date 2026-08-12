package no.beint.skald;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import no.beint.skald.barcode.BarcodeEAN;
import no.beint.skald.geom.PageSize;
import no.beint.skald.layout.Document;
import no.beint.skald.layout.element.Image;
import no.beint.skald.pdf.PdfDocument;
import no.beint.skald.pdf.PdfWriter;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BarcodeEANTest {
    @Test
    void calculatesAndValidatesChecksum() {
        assertEquals(5, BarcodeEAN.calculateEANParity("1234567890"));
        assertEquals(95, BarcodeEAN.getBarsEAN13("5901234123457").length);
        assertThrows(IllegalArgumentException.class, () -> BarcodeEAN.getBarsEAN13("5901234123458"));
    }

    @Test
    void renderedLabelIsMachineReadable() throws Exception {
        var output = new ByteArrayOutputStream();
        var pdf = new PdfDocument(new PdfWriter(output));
        var barcode = new BarcodeEAN(pdf);
        barcode.setCode("5901234123457");
        barcode.setBarHeight(48f);
        barcode.setSize(9f);
        barcode.setX(1.4f);
        var image = new Image(barcode.createFormXObject(pdf)).scaleToFit(260, 110);
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
