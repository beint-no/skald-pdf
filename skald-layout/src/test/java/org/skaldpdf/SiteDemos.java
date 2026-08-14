package org.skaldpdf;

import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.skaldpdf.invoice.no.Bank;
import org.skaldpdf.invoice.no.Company;
import org.skaldpdf.invoice.no.NorwegianInvoice;
import org.skaldpdf.invoice.no.Party;
import org.skaldpdf.labels.ProductSticker;
import org.skaldpdf.packing.no.NorwegianPackingSlip;
import org.skaldpdf.statement.no.NorwegianStatement;
import org.skaldpdf.barcode.QrCode;
import org.skaldpdf.colors.ColorConstants;
import org.skaldpdf.geom.PageSize;
import org.skaldpdf.layout.element.Image;
import org.skaldpdf.layout.element.Paragraph;

import javax.imageio.ImageIO;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Writes the public website demo PDFs and first-page previews. */
public final class SiteDemos {
    private SiteDemos() {
    }

    public static void main(String[] arguments) throws Exception {
        var directory = arguments.length > 0
            ? Path.of(arguments[0])
            : Path.of("site", "demos");
        Files.createDirectories(directory);
        write(directory, "invoice", invoice());
        write(directory, "sticker", ProductSticker.pdf(ProductStickerTest.SOJA_BA_L));
        write(directory, "sticker-sheet", ProductSticker.sheet(List.of(
            ProductStickerTest.SOJA_BA_L,
            ProductStickerTest.SOJA_BA_L,
            ProductStickerTest.SOJA_BA_L,
            ProductStickerTest.SOJA_BA_L
        )));
        write(directory, "ticket", ticket());
        write(directory, "statement", statement());
        write(directory, "packing-slip", packingSlip());
        System.out.println("Wrote site demos to " + directory.toAbsolutePath());
    }

    private static void write(Path directory, String name, byte[] bytes) throws Exception {
        Files.write(directory.resolve(name + ".pdf"), bytes);
        try (var document = PdfTestSupport.load(bytes)) {
            var image = new PDFRenderer(document).renderImageWithDPI(0, 132, ImageType.RGB);
            ImageIO.write(image, "png", directory.resolve(name + ".png").toFile());
        }
    }

    private static byte[] invoice() {
        return NorwegianInvoice.pdf(NorwegianInvoice.Model.builder()
            .company(new Company("Northstar Ledger AS", "NO", "999888777",
                "Karl Johans gate 1, 0154 Oslo, Norge", true))
            .customer(new Party("Nordlys Butikk AS", "Storgata 10", "0184 Oslo"))
            .bank(new Bank("DNB Bank ASA", "15034567890", "NO9315034567890", "DNBANOKK"))
            .number("2026-1001")
            .issueDate(java.time.LocalDate.of(2026, 8, 12))
            .dueDate(java.time.LocalDate.of(2026, 8, 26))
            .line("Consulting August", "Retainer", 1, "12,500.00", 25)
            .line("Platform access", "", 1, "1,200.00", 25)
            .paymentQr(true)
            .build());
    }

    private static byte[] ticket() {
        return Pdf.create(new PageSize(420, 180), document -> {
            document.setMargins(18, 18, 18, 18);
            document.add(new Paragraph("NORTHSTAR SESSIONS").bold().setFontSize(16)
                .setFontColor(ColorConstants.ACCENT));
            document.add(new Paragraph("13 August 2026 · Door 19:00 · Seat C14").setMarginTop(4));
            document.add(new Image(new QrCode("https://tickets.skaldpdf.org/C14").withModuleSize(2.4f))
                .scaleToFit(72, 72).setFixedPosition(320, 40, 72));
            document.add(new Paragraph("Present this PDF 2.0 ticket at the door.")
                .setFontSize(8).setFontColor(ColorConstants.MUTED).setMarginTop(28));
        });
    }

    private static byte[] statement() {
        var builder = NorwegianStatement.Model.builder()
            .company(new Company("Northstar Ledger AS", "NO", "999888777",
                "Karl Johans gate 1, 0154 Oslo, Norge", true))
            .customer(new Party("Nordlys Butikk AS", "Storgata 10", "0184 Oslo"))
            .number("2026-07")
            .period(java.time.LocalDate.of(2026, 7, 1), java.time.LocalDate.of(2026, 7, 31))
            .openingBalance("0.00");
        for (int index = 1; index <= 8; index++) {
            if (index % 2 == 0) {
                builder.debit(java.time.LocalDate.of(2026, 7, index), "D" + index,
                    "Settlement " + index, "1,250.00");
            } else {
                builder.credit(java.time.LocalDate.of(2026, 7, index), "C" + index,
                    "Settlement " + index, "1,250.00");
            }
        }
        return NorwegianStatement.pdf(builder.build());
    }

    private static byte[] packingSlip() {
        return NorwegianPackingSlip.pdf(NorwegianPackingSlip.Model.builder()
            .company(new Company("Northstar Ledger AS", "NO", "999888777",
                "Karl Johans gate 1, 0154 Oslo, Norge", true))
            .recipient(new Party("Nordlys Butikk AS", "Storgata 10", "0184 Oslo"))
            .number("4412")
            .deliveryDate(java.time.LocalDate.of(2026, 8, 14))
            .tracking("TRACK-4412")
            .trackingUrl("https://track.skaldpdf.org/4412")
            .line("Oak tray", "SKU-018", 3, "A-12")
            .line("Linen napkin set", "SKU-044", 6, "B-04")
            .build());
    }
}
