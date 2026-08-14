package org.skaldpdf.packing.no;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import org.apache.pdfbox.Loader;
import org.junit.jupiter.api.Test;
import org.skaldpdf.Pdf;
import org.skaldpdf.invoice.no.Company;
import org.skaldpdf.invoice.no.Party;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NorwegianPackingSlipTest {
    @Test
    void packingSlipListsGoodsAndDecodesTrackingQr() throws Exception {
        var bytes = NorwegianPackingSlip.pdf(sample().build());
        assertTrue(new String(bytes, 0, 8, StandardCharsets.US_ASCII).startsWith("%PDF-2.0"));
        var text = Pdf.extractText(bytes);
        assertTrue(text.contains("Pakkseddel"));
        assertTrue(text.contains("Ordrenr."));
        assertTrue(text.contains("POSTEN373724189NO") || text.contains("POSTEN 373724189NO"), text);
        assertTrue(text.contains("REG-AUG"));
        assertTrue(text.contains("Antall"));
        assertEquals("https://sporing.posten.no/373724189NO", decodeQr(bytes));
        try (var parsed = Loader.loadPDF(bytes)) {
            assertEquals(1, parsed.getNumberOfPages());
        }
    }

    @Test
    void deliveryNoteUsesDifferentTitle() {
        var text = Pdf.extractText(NorwegianPackingSlip.pdf(sample()
            .kind(NorwegianPackingSlip.Kind.DELIVERY_NOTE)
            .build()));
        assertTrue(text.contains("Følgeseddel"));
        assertTrue(text.contains("Følgende varer er sendt"));
    }

    @Test
    void rejectsEmptySlip() {
        assertThrows(IllegalArgumentException.class, () -> NorwegianPackingSlip.Model.builder()
            .company(new Company("A", "NO", "111222333", "Oslo", true))
            .recipient(new Party("B", "Bergen"))
            .number("1")
            .deliveryDate(LocalDate.of(2026, 8, 14))
            .build());
    }

    private static NorwegianPackingSlip.Builder sample() {
        return NorwegianPackingSlip.Model.builder()
            .company(new Company("Nordlys Handel AS", "NO", "999888777",
                "Storgata 10, 0184 Oslo, Norge", true))
            .recipient(new Party("Fjordbutikken AS", "Kaien 4", "5003 Bergen"))
            .number("1001")
            .deliveryDate(LocalDate.of(2026, 8, 14))
            .tracking("POSTEN 373724189NO")
            .trackingUrl("https://sporing.posten.no/373724189NO")
            .line("Regnskapstjeneste august", "REG-AUG", 8, "A-12")
            .line("Lønnskjøring", "PAY-2026-08", 1, "A-12");
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
