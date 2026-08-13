package org.skaldpdf;

import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.skaldpdf.barcode.ProductSticker;
import org.skaldpdf.barcode.QrCode;
import org.skaldpdf.colors.ColorConstants;
import org.skaldpdf.geom.PageSize;
import org.skaldpdf.layout.element.Cell;
import org.skaldpdf.layout.element.Div;
import org.skaldpdf.layout.element.Image;
import org.skaldpdf.layout.element.Paragraph;
import org.skaldpdf.layout.element.Table;
import org.skaldpdf.layout.properties.TextAlignment;
import org.skaldpdf.layout.properties.UnitValue;

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
        return Pdf.create(document -> {
            document.setTitle("Invoice 2026-1001")
                .setAuthor("Skald PDF")
                .setLanguage("en-GB")
                .setMargins(48, 48, 44, 48)
                .setHeader(16, page -> new Paragraph("Northstar Ledger AS")
                    .setFontSize(8).setFontColor(ColorConstants.MUTED))
                .setFirstHeader(page -> new Paragraph("Northstar Ledger AS · original invoice")
                    .setFontSize(8).setFontColor(ColorConstants.ACCENT))
                .setFooter(16, page -> new Paragraph(page.pageNumber() + " / " + page.pageCount())
                    .setFontSize(8).setTextAlignment(TextAlignment.CENTER).setFontColor(ColorConstants.MUTED));
            document.add(new Paragraph("Invoice").setFontSize(11).setFontColor(ColorConstants.ACCENT));
            document.add(new Paragraph("2026-1001").bold().setFontSize(28).setFontColor(ColorConstants.INK));
            document.add(new Paragraph("Consulting August · due 26 August 2026")
                .setFontColor(ColorConstants.MUTED).setMarginTop(4));
            var parties = new Table(UnitValue.createPercentArray(new float[] {1, 1}))
                .useAllAvailableWidth().setMarginTop(22);
            parties.addCell(cell("From", "Northstar Ledger AS\nKarl Johans gate 1\n0154 Oslo"));
            parties.addCell(cell("Bill to", "Nordlys Butikk AS\nStorgata 10\n0184 Oslo"));
            document.add(parties);
            var lines = new Table(UnitValue.createPercentArray(new float[] {4, 1, 1.4f, 1.4f}))
                .useAllAvailableWidth().setMarginTop(20);
            for (var header : new String[] {"Description", "Qty", "Rate", "Amount"}) {
                lines.addHeaderCell(new Cell().add(new Paragraph(header).bold().setFontSize(8)
                    .setFontColor(ColorConstants.WHITE))
                    .setBackgroundColor(ColorConstants.ACCENT).setPadding(6));
            }
            lines.addCell(body("Consulting August"));
            lines.addCell(body("1"));
            lines.addCell(body("12 500.00"));
            lines.addCell(body("12 500.00"));
            lines.addCell(body("Platform access"));
            lines.addCell(body("1"));
            lines.addCell(body("1 200.00"));
            lines.addCell(body("1 200.00"));
            document.add(lines);
            var total = new Table(UnitValue.createPercentArray(new float[] {1, 3}))
                .useAllAvailableWidth().setMarginTop(18);
            total.addCell(new Cell().add(new Image(new QrCode("https://pay.skaldpdf.org/inv/2026-1001")
                .withModuleSize(2.4f)).scaleToFit(78, 78)));
            total.addCell(new Cell()
                .add(new Paragraph("Total NOK 13 700.00").bold().setFontSize(16)
                    .setTextAlignment(TextAlignment.RIGHT).setFontColor(ColorConstants.ACCENT))
                .add(new Paragraph("Scan to pay · Account 1503.45.67890")
                    .setFontSize(9).setTextAlignment(TextAlignment.RIGHT)
                    .setFontColor(ColorConstants.MUTED).setMarginTop(6)));
            document.add(total);
        });
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
        return Pdf.create(document -> {
            document.setMargins(48, 48, 44, 48)
                .setFooter(16, page -> new Paragraph(page.pageNumber() + " / " + page.pageCount())
                    .setFontSize(8).setTextAlignment(TextAlignment.CENTER).setFontColor(ColorConstants.MUTED));
            document.add(new Paragraph("Statement of account").bold().setFontSize(22)
                .setFontColor(ColorConstants.ACCENT));
            document.add(new Paragraph("Nordlys Butikk AS · July 2026").setFontColor(ColorConstants.MUTED));
            var table = new Table(UnitValue.createPercentArray(new float[] {1.4f, 3, 1.3f, 1.3f}))
                .useAllAvailableWidth().setMarginTop(18);
            for (var header : new String[] {"Date", "Text", "Debit", "Credit"}) {
                table.addHeaderCell(new Cell().add(new Paragraph(header).bold().setFontSize(8)
                    .setFontColor(ColorConstants.WHITE))
                    .setBackgroundColor(ColorConstants.ACCENT).setPadding(6));
            }
            for (int index = 1; index <= 8; index++) {
                table.addCell(body("%02d.07".formatted(index)));
                table.addCell(body("Settlement " + index));
                table.addCell(body(index % 2 == 0 ? "1 250.00" : ""));
                table.addCell(body(index % 2 == 0 ? "" : "1 250.00"));
            }
            document.add(table);
        });
    }

    private static byte[] packingSlip() {
        return Pdf.create(document -> {
            document.setMargins(48, 48, 44, 48);
            document.add(new Paragraph("Packing slip · #4412").bold().setFontSize(22)
                .setFontColor(ColorConstants.ACCENT));
            document.add(new Paragraph("Ship to Nordlys Butikk AS, Storgata 10, Oslo").setMarginTop(6));
            var table = new Table(UnitValue.createPercentArray(new float[] {2, 4, 1}))
                .useAllAvailableWidth().setMarginTop(16);
            for (var header : new String[] {"SKU", "Item", "Qty"}) {
                table.addHeaderCell(new Cell().add(new Paragraph(header).bold().setFontSize(8)
                    .setFontColor(ColorConstants.WHITE))
                    .setBackgroundColor(ColorConstants.ACCENT).setPadding(6));
            }
            table.addCell(body("SKU-018"));
            table.addCell(body("Oak tray"));
            table.addCell(body("3"));
            table.addCell(body("SKU-044"));
            table.addCell(body("Linen napkin set"));
            table.addCell(body("6"));
            document.add(table);
            document.add(new Div().setMarginTop(20).add(
                new Image(new QrCode("https://track.skaldpdf.org/4412").withModuleSize(2.4f)).scaleToFit(72, 72)
            ));
            document.add(new Paragraph("Scan for live tracking").setFontSize(9)
                .setFontColor(ColorConstants.MUTED).setMarginTop(6));
        });
    }

    private static Cell cell(String label, String body) {
        return new Cell()
            .add(new Paragraph(label).setFontSize(8).setFontColor(ColorConstants.MUTED))
            .add(new Paragraph(body).setFontSize(10).setMarginTop(2));
    }

    private static Cell body(String text) {
        return new Cell().add(new Paragraph(text).setFontSize(9)).setPadding(5);
    }
}
