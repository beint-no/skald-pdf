package org.skaldpdf;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.common.GlobalHistogramBinarizer;
import com.google.zxing.common.HybridBinarizer;
import org.skaldpdf.barcode.QrCode;
import org.skaldpdf.geom.PageSize;
import org.skaldpdf.layout.element.Image;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QrCodeTest {
    @Test
    void selectsACompactVersionForShortPayloads() {
        var qr = new QrCode("https://pay.skaldpdf.org/inv/2026-1001");
        assertEquals(QrCode.Ecc.M, qr.ecc());
        assertTrue(qr.version() >= 1 && qr.version() <= 5);
        assertEquals(qr.version() * 4 + 17, qr.moduleCount());
        assertEquals("https://pay.skaldpdf.org/inv/2026-1001", qr.value());
    }

    @Test
    void rejectsAnEmptyPayload() {
        assertThrows(IllegalArgumentException.class, () -> new QrCode(""));
    }

    @Test
    void renderedSymbolIsMachineReadable() throws Exception {
        var payload = "https://pay.skaldpdf.org/inv/2026-1001";
        var bytes = Pdf.create(new PageSize(320, 320), document -> {
            document.setMargins(24, 24, 24, 24);
            document.add(new Image(new QrCode(payload)).scale(272, 272));
        });
        PdfTestSupport.saveArtifacts("qr-payment", bytes);
        assertEquals(payload, decode(bytes));
    }

    @Test
    void decodesHighEccUnicodePayloads() throws Exception {
        var payload = "Kvittering 2026-Ø8 · NOK 1 250.00";
        var bytes = Pdf.create(new PageSize(360, 360), document -> {
            document.setMargins(28, 28, 28, 28);
            document.add(new Image(new QrCode(payload, QrCode.Ecc.H)).scale(304, 304));
        });
        PdfTestSupport.saveArtifacts("qr-unicode", bytes);
        assertEquals(payload, decode(bytes));
    }

    private static String decode(byte[] bytes) throws Exception {
        try (var document = PdfTestSupport.load(bytes)) {
            var rendered = new org.apache.pdfbox.rendering.PDFRenderer(document)
                .renderImageWithDPI(0, 220, org.apache.pdfbox.rendering.ImageType.RGB);
            var pixels = rendered.getRGB(0, 0, rendered.getWidth(), rendered.getHeight(), null, 0, rendered.getWidth());
            var source = new RGBLuminanceSource(rendered.getWidth(), rendered.getHeight(), pixels);
            var hints = Map.of(
                DecodeHintType.POSSIBLE_FORMATS, java.util.List.of(BarcodeFormat.QR_CODE),
                DecodeHintType.CHARACTER_SET, "UTF-8",
                DecodeHintType.TRY_HARDER, Boolean.TRUE
            );
            try {
                return new MultiFormatReader().decode(new BinaryBitmap(new GlobalHistogramBinarizer(source)), hints).getText();
            } catch (com.google.zxing.NotFoundException ignored) {
                return new MultiFormatReader().decode(new BinaryBitmap(new HybridBinarizer(source)), hints).getText();
            }
        }
    }
}
