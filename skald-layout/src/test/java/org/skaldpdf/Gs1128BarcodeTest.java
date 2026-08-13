package org.skaldpdf;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import org.skaldpdf.barcode.Gs1128Barcode;
import org.skaldpdf.geom.PageSize;
import org.skaldpdf.layout.element.Image;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Gs1128BarcodeTest {
    @Test
    void formatsAndValidatesApplicationIdentifiers() {
        var barcode = new Gs1128Barcode("(01)09501101530003(17)260815(10)BATCH");
        assertEquals("(01)09501101530003(17)260815(10)BATCH", barcode.humanReadable());
        assertTrue(barcode.encodedModules().length > 40);
        assertThrows(IllegalArgumentException.class, () -> new Gs1128Barcode("0109501101530003"));
        assertThrows(IllegalArgumentException.class, () -> new Gs1128Barcode("(01)123"));
    }

    @Test
    void renderedSymbolIsMachineReadable() throws Exception {
        var barcode = new Gs1128Barcode("(01)09501101530003")
            .withModuleWidth(1.2f)
            .withBarHeight(42f)
            .withFontSize(8f);
        var bytes = Pdf.create(new PageSize(420, 140), document -> {
            document.setMargins(16, 16, 16, 16);
            document.add(new Image(barcode).scaleInto(380, 100));
        });
        var rendered = PdfTestSupport.renderFirstPage(bytes);
        var pixels = rendered.getRGB(0, 0, rendered.getWidth(), rendered.getHeight(), null, 0, rendered.getWidth());
        var source = new RGBLuminanceSource(rendered.getWidth(), rendered.getHeight(), pixels);
        var result = new MultiFormatReader().decode(
            new BinaryBitmap(new HybridBinarizer(source)),
            Map.of(DecodeHintType.POSSIBLE_FORMATS, java.util.List.of(BarcodeFormat.CODE_128),
                DecodeHintType.TRY_HARDER, Boolean.TRUE)
        );
        assertTrue(result.getText().contains("09501101530003"), result.getText());
        PdfTestSupport.saveArtifacts("gs1128-label", bytes);
    }
}
