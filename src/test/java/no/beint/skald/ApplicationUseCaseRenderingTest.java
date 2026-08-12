package no.beint.skald;

import no.beint.skald.colors.ColorConstants;
import no.beint.skald.colors.DeviceRgb;
import no.beint.skald.event.AbstractPdfDocumentEvent;
import no.beint.skald.event.AbstractPdfDocumentEventHandler;
import no.beint.skald.event.PdfDocumentEvent;
import no.beint.skald.font.PdfFontFactory;
import no.beint.skald.font.StandardFonts;
import no.beint.skald.geom.PageSize;
import no.beint.skald.image.ImageDataFactory;
import no.beint.skald.layout.Canvas;
import no.beint.skald.layout.Document;
import no.beint.skald.layout.borders.Border;
import no.beint.skald.layout.borders.SolidBorder;
import no.beint.skald.layout.element.AreaBreak;
import no.beint.skald.layout.element.Cell;
import no.beint.skald.layout.element.Div;
import no.beint.skald.layout.element.Image;
import no.beint.skald.layout.element.Paragraph;
import no.beint.skald.layout.element.Table;
import no.beint.skald.layout.properties.TextAlignment;
import no.beint.skald.layout.properties.UnitValue;
import no.beint.skald.layout.properties.VerticalAlignment;
import no.beint.skald.pdf.PdfDocument;
import no.beint.skald.pdf.PdfWriter;
import no.beint.skald.pdf.canvas.PdfCanvas;
import no.beint.skald.pdf.extgstate.PdfExtGState;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ApplicationUseCaseRenderingTest {
    private static final DeviceRgb GREEN = new DeviceRgb(24, 83, 63);
    private static final DeviceRgb PALE_GREEN = new DeviceRgb(232, 244, 238);
    private static final DeviceRgb ORANGE = new DeviceRgb(180, 70, 0);

    @TestFactory
    Stream<DynamicTest> rendersEveryApplicationUseCase() {
        return Stream.of(
            useCase("invoice-order-offer", this::invoiceOrderOffer, "Invoice 2026-1001", "Total NOK"),
            useCase("invoice-reminder", this::invoiceReminder, "Payment reminder", "Interest"),
            useCase("agreements", this::agreements, "SERVICE AGREEMENT", "Signatures"),
            useCase("annual-account-notes", this::annualAccountNotes, "Notes to the annual accounts", "Going concern"),
            useCase("attachment-image", this::attachmentImage, "Expense attachment"),
            useCase("ehf-watermark", this::ehfWatermark, "EHF Invoice", "PREVIEW"),
            useCase("pos-receipt", this::posReceipt, "SALES RECEIPT", "TOTAL NOK"),
            useCase("pos-z-report", this::zReport, "Z REPORT", "Payment methods"),
            useCase("payslips", this::payslips, "PAYSLIP", "Year to date"),
            useCase("shopify-transactions", this::shopifyTransactions, "Shopify transactions", "Cross-period refund"),
            useCase("tax-return-receipt", this::taxReturnReceipt, "Tax return receipt", "Validation result"),
            useCase("inventory-stock-list", this::stockList, "Stock list", "Total stock value"),
            useCase("generic-reports", this::genericReport, "General ledger", "Opening balance")
        ).map(useCase -> DynamicTest.dynamicTest(useCase.name(), () -> {
            var bytes = useCase.generator().get();
            PdfTestSupport.saveArtifacts(useCase.name(), bytes);
            var text = PdfTestSupport.text(bytes);
            for (var expected : useCase.expectedText()) {
                var normalizedText = text.replaceAll("\\s+", "");
                var normalizedExpected = expected.replaceAll("\\s+", "");
                assertTrue(text.contains(expected) || normalizedText.contains(normalizedExpected),
                    () -> useCase.name() + " is missing text: " + expected);
            }
            PdfTestSupport.assertVisibleInk(PdfTestSupport.renderFirstPage(bytes));
        }));
    }

    private UseCase useCase(String name, Supplier<byte[]> generator, String... expected) {
        return new UseCase(name, generator, List.of(expected));
    }

    private byte[] invoiceOrderOffer() {
        return create(PageSize.A4, 40, "Invoice 2026-1001", (pdf, document) -> {
            try {
                var header = new Table(UnitValue.createPercentArray(new float[] { 45, 55 })).useAllAvailableWidth()
                    .setBorder(Border.NO_BORDER);
                header.addCell(new Cell().setBorder(Border.NO_BORDER).add(
                    new Image(ImageDataFactory.create(PdfTestSupport.sampleLogo())).scaleToFit(145, 48)
                ));
                header.addCell(new Cell().setBorder(Border.NO_BORDER).add(
                    new Paragraph("Invoice 2026-1001").simulateBold().setFontSize(19).setTextAlignment(TextAlignment.RIGHT)
                ));
                document.add(header);
            } catch (Exception exception) {
                throw new IllegalStateException(exception);
            }
            document.add(new Paragraph("Skald Commerce AS\nKarl Johans gate 1\n0154 Oslo").setFontSize(10));
            document.add(new Paragraph("Customer: Nordlys Butikk AS\nStorgata 10, 0184 Oslo").setMarginTop(14));
            var lines = table(new float[] { 32, 10, 14, 10, 14, 20 },
                "Description", "Qty", "Unit price", "VAT", "Excl. VAT", "Incl. VAT");
            for (int index = 1; index <= 24; index++) {
                row(lines, "Modern accounting service " + index, "1", "1 250.00", "25%", "1 250.00", "1 562.50");
            }
            row(lines, "", "", "", "", "Total NOK", "37 500.00");
            document.add(lines.setMarginTop(22));
            document.add(new Paragraph("Due 26.08.2026 · Account 1503.45.67890").simulateBold().setMarginTop(14));
        });
    }

    private byte[] invoiceReminder() {
        return create(PageSize.A4, 40, "Payment reminder", (pdf, document) -> {
            document.add(title("Payment reminder"));
            document.add(new Paragraph("Invoice 2026-1001 remains unpaid. Please use the invoice number as reference."));
            var activity = table(new float[] { 20, 38, 20, 22 }, "Date", "Activity", "Amount", "Outstanding");
            row(activity, "12.07.2026", "Original invoice", "12 500.00", "12 500.00");
            row(activity, "01.08.2026", "Partial payment", "-2 500.00", "10 000.00");
            row(activity, "12.08.2026", "Reminder fee", "35.00", "10 035.00");
            row(activity, "12.08.2026", "Interest", "82.47", "10 117.47");
            document.add(activity.setMarginTop(18));
            document.add(new Paragraph("Amount due NOK 10 117.47").simulateBold().setFontSize(14).setMarginTop(16));
        });
    }

    private byte[] agreements() {
        return create(PageSize.A4, 44, "SERVICE AGREEMENT", (pdf, document) -> {
            document.add(title("SERVICE AGREEMENT"));
            document.add(new Paragraph("Between Beint Accounting AS and Nordlys Handel AS").setTextAlignment(TextAlignment.CENTER));
            for (int section = 1; section <= 14; section++) {
                var block = new Div().setKeepTogether(true);
                block.add(new Paragraph(section + ". Section " + section).simulateBold().setFontSize(12).setKeepWithNext(true));
                block.add(new Paragraph(
                    "The parties agree that services are delivered efficiently, securely, and in accordance with applicable Norwegian requirements. "
                        + "Responsibilities, deadlines, documentation, and communication are described in clear and modern terms."
                ).setFontSize(10).setMultipliedLeading(1.25f));
                document.add(block.setMarginTop(8));
            }
            document.add(new Paragraph("Signatures").simulateBold().setFontSize(14).setMarginTop(18));
            var signatures = new Table(UnitValue.createPercentArray(new float[] { 1, 1 })).useAllAvailableWidth();
            row(signatures, "For Beint Accounting AS\n\n____________________", "For Nordlys Handel AS\n\n____________________");
            document.add(signatures);
        });
    }

    private byte[] annualAccountNotes() {
        return create(PageSize.A4, 48, "Notes to the annual accounts", (pdf, document) -> {
            document.add(title("Notes to the annual accounts 2025"));
            document.add(new Paragraph("Nordlys Handel AS").setTextAlignment(TextAlignment.CENTER).setMarginBottom(20));
            for (var note : List.of(
                "Accounting policies|The annual accounts are prepared under the Norwegian Accounting Act.",
                "Employees|Average number of full-time equivalents: 6.4.",
                "Going concern|The board confirms that the going concern assumption remains appropriate.",
                "Related parties|All material related-party transactions are disclosed at arm's length."
            )) {
                var parts = note.split("\\|");
                document.add(new Paragraph(parts[0]).simulateBold().setFontSize(13).setMarginTop(10).setKeepWithNext(true));
                document.add(new Paragraph(parts[1]).setFontSize(11).setMultipliedLeading(1.25f));
            }
        });
    }

    private byte[] attachmentImage() {
        return create(PageSize.LETTER, 0, "Expense attachment", (pdf, document) -> {
            try {
                var image = new Image(ImageDataFactory.create(PdfTestSupport.sampleLogo())).scaleToFit(520, 680);
                image.setHorizontalAlignment(no.beint.skald.layout.properties.HorizontalAlignment.CENTER);
                document.add(image.setMarginTop(70));
            } catch (Exception exception) {
                throw new IllegalStateException(exception);
            }
            document.add(new Paragraph("Expense attachment · uploaded 12.08.2026")
                .setFixedPosition(36, 18, 540).setTextAlignment(TextAlignment.CENTER).setBorderTop(new SolidBorder(GREEN, 1)));
        });
    }

    private byte[] ehfWatermark() {
        var output = new ByteArrayOutputStream();
        var pdf = new PdfDocument(new PdfWriter(output));
        pdf.addEventHandler(PdfDocumentEvent.END_PAGE, new AbstractPdfDocumentEventHandler() {
            @Override
            protected void onAcceptedEvent(AbstractPdfDocumentEvent event) {
                var pdfEvent = (PdfDocumentEvent) event;
                var page = pdfEvent.getPage();
                var canvasBackend = new PdfCanvas(page.newContentStreamAfter(), page.getResources(), pdfEvent.getDocument());
                canvasBackend.setExtGState(new PdfExtGState().setFillOpacity(0.14f));
                new Canvas(canvasBackend, page.getPageSize())
                    .setFont(PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD))
                    .setFontSize(64).setFontColor(ColorConstants.GRAY)
                    .showTextAligned("PREVIEW", page.getPageSize().getWidth() / 2, page.getPageSize().getHeight() / 2,
                        TextAlignment.CENTER, VerticalAlignment.MIDDLE, (float) Math.toRadians(45));
            }
        });
        var document = new Document(pdf, PageSize.A4);
        document.setMargins(40, 40, 40, 40);
        document.add(title("EHF Invoice 2026-88"));
        var parties = new Table(UnitValue.createPercentArray(new float[] { 1, 1 })).useAllAvailableWidth();
        row(parties, "Supplier\nSkald Commerce AS\nNO999888777MVA", "Customer\nNordlys Butikk AS\nNO988777666MVA");
        document.add(parties);
        var lines = table(new float[] { 45, 12, 18, 12, 13 }, "Item", "Qty", "Price", "VAT", "Amount");
        for (int index = 1; index <= 35; index++) {
            row(lines, "UBL invoice line " + index, "1", "100.00", "25%", "125.00");
        }
        document.add(lines.setMarginTop(18));
        document.close();
        return output.toByteArray();
    }

    private byte[] posReceipt() {
        return create(new PageSize(226.77f, 841.89f), 13, "SALES RECEIPT", (pdf, document) -> {
            document.add(new Paragraph("SALES RECEIPT").simulateBold().setFontSize(15).setTextAlignment(TextAlignment.CENTER));
            document.add(new Paragraph("Skald Coffee · Terminal 2\nReceipt 0001042 · 12.08.2026 07:43").setFontSize(8));
            var lines = table(new float[] { 50, 14, 18, 18 }, "Product", "Qty", "VAT", "Amount");
            row(lines, "Coffee", "2", "25%", "78.00");
            row(lines, "Cinnamon bun", "1", "15%", "45.00");
            row(lines, "Reusable cup", "1", "25%", "129.00");
            document.add(lines.setMarginTop(10));
            document.add(new Paragraph("TOTAL NOK 252.00").simulateBold().setFontSize(12).setTextAlignment(TextAlignment.RIGHT));
            document.add(new Paragraph("Card · AID A0000000041010\nThank you!").setFontSize(8).setMarginTop(8));
        });
    }

    private byte[] zReport() {
        return create(PageSize.A4, 30, "Z REPORT", (pdf, document) -> {
            document.add(title("Z REPORT 2026-08-12 #74"));
            var totals = table(new float[] { 68, 32 }, "Category", "Amount");
            for (var row : List.of("Gross sales|18 420.00", "Net sales|15 076.80", "VAT|3 343.20",
                "Returns|-420.00", "Cash difference|0.00")) {
                row(totals, (Object[]) row.split("\\|"));
            }
            document.add(totals);
            document.add(new Paragraph("Payment methods").simulateBold().setFontSize(13).setMarginTop(16));
            var payments = table(new float[] { 60, 40 }, "Method", "Amount");
            row(payments, "Visa", "11 560.00");
            row(payments, "Vipps", "5 920.00");
            row(payments, "Cash", "940.00");
            document.add(payments);
        });
    }

    private byte[] payslips() {
        return create(PageSize.A4, 32, "PAYSLIP", (pdf, document) -> {
            for (int employee = 1; employee <= 2; employee++) {
                if (employee > 1) {
                    document.add(new AreaBreak());
                }
                document.add(title("PAYSLIP · August 2026"));
                document.add(new Paragraph("Employee " + employee + " · Skald Commerce AS"));
                var lines = table(new float[] { 16, 34, 12, 12, 13, 13 }, "Code", "Description", "Qty", "Rate", "Amount", "YTD");
                row(lines, "1000", "Monthly salary", "1", "55 000", "55 000", "440 000");
                row(lines, "3000", "Tax deduction", "", "", "-16 500", "-132 000");
                row(lines, "5000", "Pension", "", "", "-1 100", "-8 800");
                document.add(lines.setMarginTop(18));
                document.add(new Paragraph("Net pay NOK 37 400").simulateBold().setFontSize(14).setTextAlignment(TextAlignment.RIGHT));
                document.add(new Paragraph("Year to date").simulateBold().setFontSize(12).setMarginTop(18));
            }
        });
    }

    private byte[] shopifyTransactions() {
        return create(PageSize.A4.rotate(), 30, "Shopify transactions", (pdf, document) -> {
            document.add(title("Shopify transactions · July 2026"));
            document.add(new Paragraph("Cross-period refund rows are highlighted because the order originated in another period.").setFontSize(9));
            var lines = table(new float[] { 11, 14, 14, 10, 7, 7, 14, 12, 11 },
                "Processed", "Order", "Tx ID", "Kind", "Country", "NO?", "Gateway", "Amount", "Fee");
            for (int index = 1; index <= 42; index++) {
                var cell = body(index == 17 ? "Cross-period refund" : (index % 7 == 0 ? "Refund" : "Capture"));
                if (index == 17) {
                    cell.setFontColor(ORANGE).simulateBold();
                }
                lines.addCell(body("%02d.07.26".formatted(index % 28 + 1)));
                lines.addCell(body("#10%03d".formatted(index)));
                lines.addCell(body("gid/%06d".formatted(index)));
                lines.addCell(cell);
                lines.addCell(body(index % 3 == 0 ? "SE" : "NO"));
                lines.addCell(body(index % 3 == 0 ? "no" : "yes"));
                lines.addCell(body(index % 2 == 0 ? "Shopify" : "Klarna"));
                lines.addCell(body(index == 17 ? "-799.00" : "499.00").setTextAlignment(TextAlignment.RIGHT));
                lines.addCell(body("12.50").setTextAlignment(TextAlignment.RIGHT));
            }
            document.add(lines.setMarginTop(14));
        });
    }

    private byte[] taxReturnReceipt() {
        return create(PageSize.A4, 28, "Tax return receipt", (pdf, document) -> {
            document.setFontSize(8.5f);
            document.add(title("Tax return receipt · 2025"));
            var metadata = table(new float[] { 1, 2, 1, 2 }, "Company", "Nordlys Handel AS", "Organisation", "999 888 777");
            document.add(metadata);
            document.add(new Paragraph("Validation result").simulateBold().setFontSize(12).setMarginTop(14));
            var fields = table(new float[] { 20, 45, 35 }, "Section", "Field", "Reported value");
            for (int index = 1; index <= 38; index++) {
                row(fields, "Section " + (index / 6 + 1), "Tax value field " + index, "%d 000".formatted(index * 3));
            }
            document.add(fields);
        });
    }

    private byte[] stockList() {
        return create(PageSize.A4, 36, "Stock list", (pdf, document) -> {
            document.add(title("Stock list as of " + LocalDate.of(2026, 8, 12)));
            document.add(new Paragraph("Total stock value: NOK 184 420.50").simulateBold().setFontSize(12));
            var stock = table(new float[] { 4, 2, 1, 2, 2 }, "Title", "SKU", "Qty", "Unit cost", "Total cost");
            for (int index = 1; index <= 55; index++) {
                row(stock, "Product variant with descriptive name " + index, "SKU-%04d".formatted(index),
                    Integer.toString(index + 2), "125.50", "% ,.2f".formatted((index + 2) * 125.5));
            }
            document.add(stock.setMarginTop(10));
        });
    }

    private byte[] genericReport() {
        return create(PageSize.A3.rotate(), 32, "General ledger", (pdf, document) -> {
            document.add(title("General ledger · 01.01.2026–12.08.2026"));
            var filters = new Table(new float[] { 1, 1, 1 }).setWidth(UnitValue.createPercentValue(100));
            row(filters, "Company\nNordlys Handel AS", "Currency\nNOK", "Status\nPosted");
            document.add(filters.setMarginBottom(12));
            var report = table(new float[] { 9, 10, 10, 34, 12, 12, 13 },
                "Date", "Voucher", "Account", "Description", "Debit", "Credit", "Balance").setFixedLayout();
            for (int index = 1; index <= 90; index++) {
                row(report, "12.08.26", "V-%04d".formatted(index), index == 1 ? "1500" : "1920",
                    index == 1 ? "Opening balance" : "Efficient modern transaction description " + index,
                    index % 2 == 0 ? "1 250.00" : "", index % 2 == 0 ? "" : "1 250.00", "% ,.2f".formatted(index * 1250.0));
            }
            document.add(report);
        });
    }

    private byte[] create(PageSize pageSize, float margin, String title,
                          java.util.function.BiConsumer<PdfDocument, Document> content) {
        var output = new ByteArrayOutputStream();
        var pdf = new PdfDocument(new PdfWriter(output));
        pdf.getDocumentInfo().setTitle(title).setAuthor("Beint AS");
        var document = new Document(pdf, pageSize);
        document.setMargins(margin, margin, margin, margin);
        content.accept(pdf, document);
        document.close();
        return output.toByteArray();
    }

    private static Paragraph title(String text) {
        return new Paragraph(text).simulateBold().setFontSize(19).setMarginBottom(12);
    }

    private static Table table(float[] widths, String... headers) {
        var table = new Table(UnitValue.createPercentArray(widths)).useAllAvailableWidth();
        for (var header : headers) {
            table.addHeaderCell(new Cell().add(new Paragraph(header).simulateBold().setFontSize(8))
                .setBackgroundColor(PALE_GREEN).setBorder(new SolidBorder(GREEN, 0.5f)).setPadding(4));
        }
        return table;
    }

    private static void row(Table table, Object... values) {
        for (var value : values) {
            table.addCell(body(String.valueOf(value)));
        }
    }

    private static Cell body(String value) {
        return new Cell().add(new Paragraph(value).setFontSize(8).setMultipliedLeading(1.05f))
            .setBorder(new SolidBorder(ColorConstants.LIGHT_GRAY, 0.35f)).setPadding(4);
    }

    private record UseCase(String name, Supplier<byte[]> generator, List<String> expectedText) {
    }
}
