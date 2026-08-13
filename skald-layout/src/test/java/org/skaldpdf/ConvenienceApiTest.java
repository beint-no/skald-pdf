package org.skaldpdf;

import org.skaldpdf.barcode.UpcABarcode;
import org.skaldpdf.geom.PageSize;
import org.skaldpdf.layout.element.ListBlock;
import org.skaldpdf.layout.element.Paragraph;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConvenienceApiTest {
    @Test
    void writesOptionalXmpDatesAndAWatermark() throws Exception {
        var created = Instant.parse("2026-08-13T10:15:30Z");
        var bytes = Pdf.create(document -> {
            document.setMargins(40)
                .setCreationDate(created)
                .setWatermark("DRAFT")
                .addOutline("Cover");
            document.add(new Paragraph("Invoice preview").bold().setFontSize(20));
        });
        var ascii = new String(bytes, java.nio.charset.StandardCharsets.ISO_8859_1);
        assertTrue(ascii.contains("2026-08-13T10:15:30Z"));
        assertTrue(ascii.contains("CreateDate"));
        assertTrue(PdfTestSupport.text(bytes).contains("Invoice preview"));
        try (var parsed = PdfTestSupport.load(bytes)) {
            var states = parsed.getPage(0).getResources().getExtGStateNames();
            assertTrue(states != null && states.iterator().hasNext(), "watermark should register an opacity state");
        }
        try (var parsed = PdfTestSupport.load(bytes)) {
            assertTrue(parsed.getDocumentCatalog().getDocumentOutline() != null);
        }
        PdfTestSupport.saveArtifacts("draft-watermark", bytes);
    }

    @Test
    void numbersAListFromACustomStart() throws Exception {
        var bytes = Pdf.create(document -> document.add(
            new ListBlock(ListBlock.Marker.DECIMAL).startAt(4)
                .add("Fourth finding")
                .add("Fifth finding")
        ));
        var text = PdfTestSupport.text(bytes);
        assertTrue(text.contains("4."));
        assertTrue(text.contains("5."));
        assertTrue(text.contains("Fourth finding"));
    }

    @Test
    void buildsIsoAndImperialPageSizes() {
        var label = PageSize.ofMillimetres(93, 35);
        assertEquals(93 * 72f / 25.4f, label.getWidth(), 0.01f);
        assertEquals(35 * 72f / 25.4f, label.getHeight(), 0.01f);
        assertEquals(612f, PageSize.ofInches(8.5f, 11f).getWidth(), 0.01f);
    }

    @Test
    void upcADelegatesToEan13AndDecodes() throws Exception {
        var barcode = new UpcABarcode("03600029145");
        assertEquals("036000291452", barcode.value());
        assertEquals("0036000291452", barcode.ean13Value());
        var bytes = Pdf.create(new PageSize(360, 140), document -> {
            document.setMargins(16);
            document.add(new org.skaldpdf.layout.element.Image(barcode.withBarHeight(42f)).scaleInto(320, 100));
        });
        var rendered = PdfTestSupport.renderFirstPage(bytes);
        var pixels = rendered.getRGB(0, 0, rendered.getWidth(), rendered.getHeight(), null, 0, rendered.getWidth());
        var source = new com.google.zxing.RGBLuminanceSource(rendered.getWidth(), rendered.getHeight(), pixels);
        var result = new com.google.zxing.MultiFormatReader().decode(
            new com.google.zxing.BinaryBitmap(new com.google.zxing.common.HybridBinarizer(source)),
            java.util.Map.of(com.google.zxing.DecodeHintType.POSSIBLE_FORMATS,
                java.util.List.of(com.google.zxing.BarcodeFormat.UPC_A, com.google.zxing.BarcodeFormat.EAN_13))
        );
        assertTrue(result.getText().contains("36000291452") || result.getText().contains("036000291452"),
            result.getText());
        PdfTestSupport.saveArtifacts("upca-label", bytes);
    }
}
