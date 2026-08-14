package org.skaldpdf.invoice.no;

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

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NorwegianInvoiceTest {
    private static final Company NORDLYS = new Company(
        "Nordlys Handel AS", "NO", "999888777", "Storgata 10, 0184 Oslo, Norge", true);
    private static final Party FJORD = new Party("Fjordbutikken AS", "Kaien 4", "5003 Bergen");
    private static final Bank DNB = new Bank("DNB Bank ASA", "15034567890", "NO9315034567890", "DNBANOKK");

    @Test
    void norwegianInvoiceHasReaiLabelsAndComputedTotal() throws Exception {
        var model = sample().build();
        assertEquals(new BigDecimal("15625.00"), model.totals().incVat());
        var bytes = NorwegianInvoice.pdf(model);
        assertPdf20(bytes);
        var text = Pdf.extractText(bytes);
        for (var expected : List.of(
            "Faktura", "Fakturanr.", "1001", "Fakturadato:", "12.08.2026",
            "Organisasjonsnummer:", "NO999888777MVA",
            "Betalingsinformasjon", "Forfallsdato:", "26.08.2026",
            "Kontonummer", "1503 4567 890", "IBAN", "NO93 1503 4567 890",
            "Beskrivelse", "Antall", "MVA", "Til betaling",
            "Vennligst oppgi fakturanummer", "ved betaling",
            "NOK 15,625.00", "Nordlys Handel AS", "Fjordbutikken AS"
        )) {
            assertTrue(text.contains(expected), "Missing: " + expected + "\n" + text);
        }
        assertTrue(!text.contains("Antal\nl") && !text.matches("(?s).*Antal\\s+l\\b.*"), text);
        try (var parsed = Loader.loadPDF(bytes)) {
            assertEquals(1, parsed.getNumberOfPages());
        }
        assertVisibleInk(render(bytes));
    }

    @Test
    void creditNoteAndPaidCopyUseDedicatedTitles() {
        var credit = Pdf.extractText(NorwegianInvoice.pdf(NorwegianInvoice.Model.builder()
            .kind(NorwegianInvoice.Kind.CREDIT_NOTE)
            .company(NORDLYS).customer(FJORD).bank(DNB)
            .number("9001")
            .issueDate(LocalDate.of(2026, 8, 12))
            .line(new LineItem("Regnskapstjeneste august", "Kreditert",
                new BigDecimal("8"), new BigDecimal("-1250.00"), new BigDecimal("25")))
            .creditFor("1001", LocalDate.of(2026, 8, 12))
            .build()));
        assertTrue(credit.contains("Kreditnota"));
        assertTrue(credit.contains("Kreditnota for faktura 1001 datert 12.08.2026"));
        assertTrue(credit.contains("Beløp"));

        var paid = Pdf.extractText(NorwegianInvoice.pdf(sample()
            .kind(NorwegianInvoice.Kind.PAID_COPY)
            .paid("15,625.00", LocalDate.of(2026, 8, 20), true)
            .build()));
        assertTrue(paid.contains("Betalt fakturakopi"));
        assertTrue(paid.contains("Betalingskvittering"));
        assertTrue(paid.contains("Betalt beløp"));
        assertTrue(paid.contains("Utestående beløp"));
        assertTrue(paid.contains("NOK 0.00"));
    }

    @Test
    void quoteOrderAndProformaChangeChrome() {
        var quote = Pdf.extractText(NorwegianInvoice.pdf(sample()
            .kind(NorwegianInvoice.Kind.QUOTE)
            .number("Q-88")
            .build()));
        assertTrue(quote.contains("Tilbud"));
        assertTrue(quote.contains("Tilbudsnr."));
        assertTrue(quote.contains("Gyldig til:"));
        assertTrue(quote.contains("Tilbudssum"));
        assertTrue(!quote.contains("Betalingsinformasjon"), quote);

        var order = Pdf.extractText(NorwegianInvoice.pdf(sample()
            .kind(NorwegianInvoice.Kind.ORDER_CONFIRMATION)
            .number("5512")
            .build()));
        assertTrue(order.contains("Ordrebekreftelse"));
        assertTrue(order.contains("Ordrenr."));

        var proforma = Pdf.extractText(NorwegianInvoice.pdf(sample()
            .kind(NorwegianInvoice.Kind.PROFORMA)
            .watermark("PROFORMA")
            .build()));
        assertTrue(proforma.contains("Proforma"));
        assertTrue(proforma.contains("Dette er ikke en MVA-faktura"));
    }

    @Test
    void englishLabelsAndDiscountColumn() {
        var english = Pdf.extractText(NorwegianInvoice.pdf(sample()
            .language(NorwegianInvoice.Language.EN)
            .number("1044")
            .build()));
        assertTrue(english.contains("Invoice"));
        assertTrue(english.contains("Payable"));
        assertTrue(english.contains("Company Number:"));

        var discount = Pdf.extractText(NorwegianInvoice.pdf(NorwegianInvoice.Model.builder()
            .company(NORDLYS).customer(FJORD).bank(DNB)
            .number("1002")
            .issueDate(LocalDate.of(2026, 8, 12))
            .dueDate(LocalDate.of(2026, 8, 26))
            .line("Konsulenttimer", "Avtalt rabatt", new BigDecimal("10"),
                new BigDecimal("1250.00"), new BigDecimal("10"), new BigDecimal("25"))
            .build()));
        assertTrue(discount.contains("Rabatt"));
        assertTrue(discount.contains("10 %"));
        assertTrue(discount.contains("14,062.50"));
    }

    @Test
    void longInvoicePaginatesAndQrDecodes() throws Exception {
        var builder = NorwegianInvoice.Model.builder()
            .company(NORDLYS).customer(FJORD).bank(DNB)
            .number("1088")
            .issueDate(LocalDate.of(2026, 8, 12))
            .dueDate(LocalDate.of(2026, 8, 26));
        for (int index = 1; index <= 28; index++) {
            builder.line("Modern accounting service " + index, "Periode " + index, 1, "1,250.00", 25);
        }
        var longInvoice = NorwegianInvoice.pdf(builder.build());
        try (var parsed = Loader.loadPDF(longInvoice)) {
            assertTrue(parsed.getNumberOfPages() >= 2, "28-line invoice should paginate");
        }

        var withQr = NorwegianInvoice.pdf(sample().paymentQr(true).logo(sampleLogo()).build());
        var payload = decodeQr(withQr);
        assertTrue(payload.contains("NO9315034567890"), payload);
        assertTrue(payload.contains("15625.00"), payload);
        assertTrue(Pdf.extractText(withQr).contains("Betaling med QR"));
    }

    @Test
    void mixedVatRatesGetSeparateSpecificationRows() {
        var text = Pdf.extractText(NorwegianInvoice.pdf(NorwegianInvoice.Model.builder()
            .company(NORDLYS).customer(FJORD).bank(DNB)
            .number("1100")
            .issueDate(LocalDate.of(2026, 8, 12))
            .dueDate(LocalDate.of(2026, 8, 26))
            .line("Mat", "", 1, "100.00", 15)
            .line("Tjeneste", "", 1, "100.00", 25)
            .build()));
        assertTrue(text.contains("15 %"), text);
        assertTrue(text.contains("25 %"), text);
    }

    @Test
    void rejectsIncompleteModels() {
        assertThrows(IllegalArgumentException.class, () -> sample().number("").build());
        assertThrows(IllegalArgumentException.class, () -> sample()
            .kind(NorwegianInvoice.Kind.CREDIT_NOTE).build());
        assertThrows(IllegalArgumentException.class, () -> sample().paymentQr(true).bank(null).build());
        assertThrows(IllegalArgumentException.class, () -> NorwegianInvoice.Model.builder()
            .company(NORDLYS).customer(FJORD).number("1")
            .issueDate(LocalDate.of(2026, 8, 12)).build());
    }

    private static NorwegianInvoice.Builder sample() {
        return NorwegianInvoice.Model.builder()
            .company(NORDLYS)
            .customer(FJORD)
            .bank(DNB)
            .number("1001")
            .issueDate(LocalDate.of(2026, 8, 12))
            .dueDate(LocalDate.of(2026, 8, 26))
            .ourReference("Kari Nord")
            .buyerReference("Ola Fjord")
            .line("Regnskapstjeneste august", "Løpende avtale", 8, "1,250.00", 25)
            .line("Lønnskjøring", "", 1, "2,500.00", 25);
    }

    private static void assertPdf20(byte[] bytes) {
        assertTrue(new String(bytes, 0, 8, StandardCharsets.US_ASCII).startsWith("%PDF-2.0"));
    }

    private static BufferedImage render(byte[] bytes) throws Exception {
        try (var document = Loader.loadPDF(bytes)) {
            return new PDFRenderer(document).renderImageWithDPI(0, 144, ImageType.RGB);
        }
    }

    private static void assertVisibleInk(BufferedImage image) {
        var dark = 0;
        for (int y = 0; y < image.getHeight(); y += 2) {
            for (int x = 0; x < image.getWidth(); x += 2) {
                if ((image.getRGB(x, y) & 0x00ff_ffff) != 0x00ff_ffff) {
                    dark++;
                }
            }
        }
        var ratio = dark / (image.getWidth() * image.getHeight() / 4.0);
        assertTrue(ratio > 0.002 && ratio < 0.70, "visible ink ratio=" + ratio);
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

    static byte[] sampleLogo() throws Exception {
        var image = new BufferedImage(240, 80, BufferedImage.TYPE_INT_RGB);
        var graphics = image.createGraphics();
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
        graphics.setColor(new Color(24, 83, 63));
        graphics.fillRoundRect(4, 4, 232, 72, 16, 16);
        graphics.setColor(Color.WHITE);
        graphics.setFont(new java.awt.Font(java.awt.Font.SANS_SERIF, java.awt.Font.BOLD, 34));
        graphics.drawString("SKALD", 50, 53);
        graphics.dispose();
        var output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }
}
