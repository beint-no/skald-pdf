package org.skaldpdf;

import org.skaldpdf.barcode.Code128Barcode;
import org.skaldpdf.barcode.Ean13Barcode;
import org.skaldpdf.barcode.Gs1128Barcode;
import org.skaldpdf.invoice.no.Company;
import org.skaldpdf.invoice.no.NorwegianInvoice;
import org.skaldpdf.invoice.no.Party;
import org.skaldpdf.labels.ProductSticker;
import org.skaldpdf.labels.shipping.ShippingLabel;
import org.skaldpdf.purchase.no.NorwegianPurchaseOrder;
import org.skaldpdf.receipt.no.NorwegianReceipt;
import org.skaldpdf.statement.no.NorwegianStatement;
import org.skaldpdf.barcode.QrCode;
import org.skaldpdf.barcode.UpcABarcode;
import org.skaldpdf.colors.ColorConstants;
import org.skaldpdf.colors.DeviceRgb;
import org.skaldpdf.geom.PageSize;
import org.skaldpdf.layout.borders.SolidBorder;
import org.skaldpdf.layout.element.Cell;
import org.skaldpdf.layout.element.Image;
import org.skaldpdf.layout.element.Paragraph;
import org.skaldpdf.layout.element.Table;
import org.skaldpdf.layout.properties.TextAlignment;
import org.skaldpdf.layout.properties.UnitValue;
import org.skaldpdf.reai.ReaiStyleDocuments;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/** Large corpus of the PDFs an accounting / commerce system actually ships. */
public final class TypicalBusinessDocuments {
    private static final DeviceRgb INK = ColorConstants.INK;
    private static final DeviceRgb ACCENT = ColorConstants.ACCENT;

    private TypicalBusinessDocuments() {
    }

    public static Map<String, NamedDocument> all(byte[] logo) {
        var documents = new LinkedHashMap<String, NamedDocument>();
        add(documents, "invoice-no", "Faktura 1001", List.of("Faktura", "Til betaling", "NO999888777MVA"),
            () -> ReaiStyleDocuments.invoice(ReaiStyleDocuments.sampleInvoice(), logo));
        add(documents, "invoice-en", "Invoice 1044", List.of("Invoice", "Payable", "Company Number:"),
            () -> ReaiStyleDocuments.invoice(ReaiStyleDocuments.englishInvoice(), logo));
        add(documents, "invoice-discount", "Faktura 1002", List.of("Rabatt", "10 %"),
            () -> ReaiStyleDocuments.invoice(ReaiStyleDocuments.discountInvoice(), logo));
        add(documents, "credit-note", "Kreditnota 9001", List.of("Kreditnota", "Kreditnota for faktura"),
            () -> ReaiStyleDocuments.invoice(ReaiStyleDocuments.creditNote(), logo));
        add(documents, "paid-copy", "Betalt fakturakopi 1001", List.of("Betalt fakturakopi", "Betalingskvittering"),
            () -> ReaiStyleDocuments.invoice(ReaiStyleDocuments.paidCopy(), logo));
        add(documents, "invoice-preview", "Faktura preview", List.of("Forhåndsvisning", "Utkast linje"),
            () -> ReaiStyleDocuments.invoice(ReaiStyleDocuments.previewInvoice(), logo));
        add(documents, "invoice-partial", "Faktura 1003", List.of("Utestående beløp", "7,500.00"),
            () -> ReaiStyleDocuments.invoice(ReaiStyleDocuments.partiallyPaidInvoice(), logo));
        add(documents, "invoice-multipage", "Faktura 1088", List.of("Modern accounting service 28", "Til betaling"),
            () -> ReaiStyleDocuments.invoice(ReaiStyleDocuments.longInvoice(), logo));
        add(documents, "invoice-qr", "Faktura 1001 QR", List.of("Betaling med QR", "Faktura"),
            () -> ReaiStyleDocuments.invoiceWithQr(ReaiStyleDocuments.sampleInvoice(), logo));
        add(documents, "order-confirmation", "Ordrebekreftelse 5512", List.of("Ordrebekreftelse", "Ordrenr."),
            () -> ReaiStyleDocuments.orderConfirmation(ReaiStyleDocuments.sampleOrder(), logo));
        add(documents, "reminder", "Purring 1001", List.of("Purring", "Purregebyr"),
            () -> ReaiStyleDocuments.reminder(false, logo));
        add(documents, "collection-notice", "Betalingsoppfordring 1001", List.of("Betalingsoppfordring", "14 dager"),
            () -> ReaiStyleDocuments.reminder(true, logo));
        add(documents, "packing-slip", "Pakkseddel 1001", List.of("Pakkseddel", "POSTEN 373724189NO"),
            () -> ReaiStyleDocuments.packingSlip(logo));
        add(documents, "ehf-preview", "EHF 1001", List.of("Faktura", "NO999888777MVA"),
            () -> ReaiStyleDocuments.ehfPreview(logo));
        add(documents, "delivery-note", "Delivery note", List.of("Delivery note", "Shipped units"),
            () -> deliveryNote());
        add(documents, "picking-list", "Picking list", List.of("Picking list", "BIN"),
            () -> pickingList());
        add(documents, "shipping-label", "Shipping label", List.of("Posten", "5003 BERGEN", "373724189NO"),
            () -> shippingLabel());
        add(documents, "ean13-sticker", "EAN-13", List.of("SOJA-BA-L"),
            () -> ProductSticker.pdf(new ProductSticker.Spec(
                "SOJA-BA-L", "CN", "Softy Jacket", "L", "",
                "80%Nylon, 20%Lycra", "8123613319580", "Orchid")));
        add(documents, "sticker-sheet", "Sticker sheet", List.of("SOJA-BA-L"),
            () -> ProductSticker.sheet(List.of(
                new ProductSticker.Spec("SOJA-BA-L", "CN", "Softy Jacket", "L", "",
                    "80%Nylon, 20%Lycra", "8123613319580", "Orchid"),
                new ProductSticker.Spec("SOJA-BA-M", "CN", "Softy Jacket", "M", "",
                    "80%Nylon, 20%Lycra", "8123613319597", "Orchid")
            )));
        add(documents, "code128-carton", "Carton label", List.of("CARTON", "PO-5512"),
            () -> cartonLabel());
        add(documents, "gs1128-sscc", "SSCC", List.of("(00)"),
            () -> gs1Label());
        add(documents, "upca-shelf", "UPC-A", List.of("03600029145"),
            () -> upcShelf());
        add(documents, "barcode-pack", "Barcode pack", List.of("EAN-13", "Code 128", "QR"),
            () -> barcodePack());
        add(documents, "proforma", "Proforma", List.of("Proforma", "Dette er ikke en MVA-faktura"),
            () -> proforma());
        add(documents, "quote", "Quote Q-88", List.of("Tilbud", "Gyldig til"),
            () -> quote());
        add(documents, "purchase-order", "PO-2201", List.of("Innkjøpsordre", "Leveres til"),
            () -> purchaseOrder());
        add(documents, "receipt", "Receipt", List.of("Kvittering", "Å betale"),
            () -> receipt());
        add(documents, "credit-application", "Credit application", List.of("Credit application", "Approved limit"),
            () -> creditApplication());
        add(documents, "statement", "Statement", List.of("Kontooversikt", "Utgående saldo"),
            () -> statement());
        add(documents, "dunning-run", "Dunning run", List.of("Dunning run", "Ageing"),
            () -> dunningRun());
        add(documents, "vat-return", "VAT return", List.of("VAT return", "Output VAT"),
            () -> vatReturn());
        add(documents, "payslip", "Payslip", List.of("PAYSLIP", "Net pay"),
            () -> payslip());
        add(documents, "expense", "Expense report", List.of("Expense report", "Mileage"),
            () -> expense());
        add(documents, "timesheet", "Timesheet", List.of("Timesheet", "Billable"),
            () -> timesheet());
        add(documents, "inventory", "Inventory", List.of("Stock list", "SKU"),
            () -> inventory());
        add(documents, "agreement-cover", "Agreement", List.of("SERVICE AGREEMENT", "Signatures"),
            () -> agreement());
        add(documents, "board-minutes", "Board minutes", List.of("Board minutes", "Resolved"),
            () -> minutes());
        add(documents, "audit-letter", "Audit letter", List.of("Management letter", "Finding"),
            () -> auditLetter());
        add(documents, "payment-advice", "Payment advice", List.of("Payment advice", "Remitted"),
            () -> paymentAdvice());
        add(documents, "reminder-en", "Reminder EN", List.of("Payment reminder", "Interest"),
            () -> reminderEn());
        return documents;
    }

    private static void add(Map<String, NamedDocument> documents, String name, String title,
                            List<String> expected, Supplier<byte[]> generator) {
        documents.put(name, new NamedDocument(name, title, expected, generator));
    }

    private static byte[] deliveryNote() {
        return Pdf.create(document -> {
            document.setMargins(36).setTitle("Delivery note 1001");
            document.add(new Paragraph("Delivery note").bold().setFontSize(20).setFontColor(ACCENT));
            document.add(new Paragraph("Order 5512 · 14.08.2026 · Fjordbutikken AS"));
            var table = table(new float[] {4, 2, 2, 2}, "Item", "Ordered", "Shipped", "Backorder");
            row(table, "Regnskapstjeneste august", "8", "8", "0");
            row(table, "Lønnskjøring", "1", "1", "0");
            document.add(table.setMarginTop(16));
            document.add(new Paragraph("Shipped units 9").bold().setMarginTop(12));
        });
    }

    private static byte[] pickingList() {
        return Pdf.create(document -> {
            document.setMargins(28).setTitle("Picking list 5512");
            document.add(new Paragraph("Picking list").bold().setFontSize(18));
            document.add(new Paragraph("Wave 14 · 14.08.2026 07:10"));
            var table = table(new float[] {2, 4, 2, 2}, "BIN", "SKU", "Qty", "Picked");
            row(table, "A-12", "REG-AUG", "8", "☐");
            row(table, "A-12", "PAY-2026-08", "1", "☐");
            row(table, "B-04", "YEAR-ADD", "1", "☐");
            document.add(table.setMarginTop(14));
        });
    }

    private static byte[] shippingLabel() {
        return ShippingLabel.pdf(new ShippingLabel.Spec(
            new ShippingLabel.Address("Nordlys Handel AS", "Storgata 10", "0184 OSLO"),
            new ShippingLabel.Address("Fjordbutikken AS", "Kaien 4", "5003 BERGEN"),
            "373724189NO",
            "Posten Bedriftspakke",
            "PO-5512",
            "https://sporing.posten.no/373724189NO"
        ));
    }

    private static byte[] cartonLabel() {
        return Pdf.create(new PageSize(300, 200), document -> {
            document.setMargins(10).setTitle("Carton");
            document.add(new Paragraph("CARTON 1/1").bold().setFontSize(14));
            document.add(new Paragraph("PO-5512 · Nordlys Handel AS").setFontSize(9));
            document.add(new Image(new Code128Barcode("PO5512C01").withBarHeight(40)).scaleInto(260, 70));
        });
    }

    private static byte[] gs1Label() {
        return Pdf.create(new PageSize(320, 180), document -> {
            document.setMargins(10).setTitle("SSCC");
            document.add(new Paragraph("GS1-128 SSCC").bold());
            document.add(new Image(new Gs1128Barcode("(00)376123456789012341").withBarHeight(48)).scaleInto(280, 80));
            document.add(new Paragraph("(00) 376123456789012341").setFontSize(9));
        });
    }

    private static byte[] upcShelf() {
        return Pdf.create(new PageSize(240, 120), document -> {
            document.setMargins(8).setTitle("Shelf");
            document.add(new Image(new UpcABarcode("03600029145").withBarHeight(40)).scaleInto(220, 90));
        });
    }

    private static byte[] barcodePack() {
        return Pdf.create(document -> {
            document.setMargins(28).setTitle("Barcode pack");
            document.add(new Paragraph("Barcode pack").bold().setFontSize(18).setFontColor(ACCENT));
            document.add(new Paragraph("EAN-13").bold().setMarginTop(10));
            document.add(new Image(new Ean13Barcode("590123412345").withBarHeight(36)).scaleInto(260, 80));
            document.add(new Paragraph("Code 128").bold().setMarginTop(10));
            document.add(new Image(new Code128Barcode("INV1001").withBarHeight(36)).scaleInto(260, 70));
            document.add(new Paragraph("QR").bold().setMarginTop(10));
            document.add(new Image(new QrCode("https://pay.example/inv/1001")).scaleInto(96, 96));
        });
    }

    private static byte[] proforma() {
        return NorwegianInvoice.pdf(nordlysInvoice()
            .kind(NorwegianInvoice.Kind.PROFORMA)
            .number("77")
            .line("Clothing sample", "", 12, "400.00", 25)
            .build());
    }

    private static byte[] quote() {
        return NorwegianInvoice.pdf(nordlysInvoice()
            .kind(NorwegianInvoice.Kind.QUOTE)
            .number("Q-88")
            .dueDate(java.time.LocalDate.of(2026, 9, 12))
            .line("Implementation", "", 40, "1,250.00", 25)
            .build());
    }

    private static byte[] purchaseOrder() {
        return NorwegianPurchaseOrder.pdf(NorwegianPurchaseOrder.Model.builder()
            .company(nordlys())
            .supplier(new Party("Papirgrossisten AS", "Industriveien 2", "2000 Lillestrøm"))
            .shipTo(new Party("Warehouse B", "Oslo"))
            .number("PO-2201")
            .orderDate(java.time.LocalDate.of(2026, 8, 10))
            .neededBy(java.time.LocalDate.of(2026, 8, 20))
            .line("Laptop", "LAP-14", 3, "12,000.00", 25)
            .build());
    }

    private static byte[] receipt() {
        return NorwegianReceipt.pdf(NorwegianReceipt.Model.builder()
            .company(nordlys())
            .number("K-4401")
            .issuedAt(java.time.LocalDateTime.of(2026, 8, 14, 14, 30))
            .paymentMethod("Kort")
            .line("Kaffe", 2, "39.00", 25)
            .line("Bolle", 1, "45.00", 15)
            .build());
    }

    private static byte[] creditApplication() {
        return Pdf.create(document -> {
            document.setMargins(40).setTitle("Credit application");
            document.add(new Paragraph("Credit application").bold().setFontSize(20));
            document.add(new Paragraph("Customer Fjordbutikken AS · Approved limit NOK 50 000"));
        });
    }

    private static byte[] statement() {
        return NorwegianStatement.pdf(NorwegianStatement.Model.builder()
            .company(nordlys())
            .customer(new Party("Fjordbutikken AS", "Kaien 4", "5003 Bergen"))
            .number("2026-07")
            .period(java.time.LocalDate.of(2026, 7, 1), java.time.LocalDate.of(2026, 7, 31))
            .openingBalance("12,500.00")
            .debit(java.time.LocalDate.of(2026, 7, 12), "0990", "Faktura 0990", "12,500.00")
            .credit(java.time.LocalDate.of(2026, 7, 20), "BET", "Innbetaling", "12,500.00")
            .build());
    }

    private static Company nordlys() {
        return new Company("Nordlys Handel AS", "NO", "999888777",
            "Storgata 10, 0184 Oslo, Norge", true);
    }

    private static NorwegianInvoice.Builder nordlysInvoice() {
        return NorwegianInvoice.Model.builder()
            .company(nordlys())
            .customer(new Party("Fjordbutikken AS", "Kaien 4", "5003 Bergen"))
            .bank(new org.skaldpdf.invoice.no.Bank("DNB Bank ASA", "15034567890", "NO9315034567890", "DNBANOKK"))
            .issueDate(java.time.LocalDate.of(2026, 8, 12))
            .dueDate(java.time.LocalDate.of(2026, 8, 26));
    }

    private static byte[] dunningRun() {
        return Pdf.create(document -> {
            document.setMargins(32).setTitle("Dunning run");
            document.add(new Paragraph("Dunning run 09.09.2026").bold().setFontSize(18));
            var table = table(new float[] {2, 3, 2, 2, 2}, "Invoice", "Customer", "Due", "Ageing", "Amount");
            row(table, "1001", "Fjordbutikken AS", "26.08", "14d", "15 625.00");
            document.add(table.setMarginTop(12));
        });
    }

    private static byte[] vatReturn() {
        return Pdf.create(document -> {
            document.setMargins(36).setTitle("VAT return");
            document.add(new Paragraph("VAT return · term 4 2026").bold().setFontSize(18));
            var table = table(new float[] {4, 2}, "Box", "Amount");
            row(table, "Output VAT 25%", "125 000.00");
            row(table, "Input VAT", "48 000.00");
            row(table, "Payable", "77 000.00");
            document.add(table.setMarginTop(12));
        });
    }

    private static byte[] payslip() {
        return Pdf.create(document -> {
            document.setMargins(36).setTitle("Payslip");
            document.add(new Paragraph("PAYSLIP · August 2026").bold().setFontSize(18).setFontColor(ACCENT));
            var table = table(new float[] {3, 3, 2, 2}, "Code", "Description", "Amount", "YTD");
            row(table, "1000", "Monthly salary", "55 000", "440 000");
            row(table, "3000", "Tax", "-16 500", "-132 000");
            document.add(table.setMarginTop(12));
            document.add(new Paragraph("Net pay NOK 38 500").bold().setMarginTop(10));
        });
    }

    private static byte[] expense() {
        return Pdf.create(document -> {
            document.setMargins(36).setTitle("Expense");
            document.add(new Paragraph("Expense report").bold().setFontSize(18));
            var table = table(new float[] {2, 4, 2}, "Date", "Description", "Amount");
            row(table, "04.08", "Train Oslo–Bergen", "899.00");
            row(table, "05.08", "Mileage 42 km", "176.40");
            document.add(table.setMarginTop(12));
        });
    }

    private static byte[] timesheet() {
        return Pdf.create(document -> {
            document.setMargins(32).setTitle("Timesheet");
            document.add(new Paragraph("Timesheet week 33").bold().setFontSize(18));
            var table = table(new float[] {3, 2, 2, 2}, "Project", "Mon–Fri", "Billable", "Internal");
            row(table, "ReAI ledger", "32.0", "30.0", "2.0");
            document.add(table.setMarginTop(12));
        });
    }

    private static byte[] inventory() {
        return Pdf.create(document -> {
            document.setMargins(32).setTitle("Stock list");
            document.add(new Paragraph("Stock list").bold().setFontSize(18));
            var table = table(new float[] {3, 2, 1, 2}, "Title", "SKU", "Qty", "Value");
            for (int index = 1; index <= 12; index++) {
                row(table, "Variant " + index, "SKU-" + index, Integer.toString(index), "1 250.00");
            }
            document.add(table.setMarginTop(10));
        });
    }

    private static byte[] agreement() {
        return Pdf.create(document -> {
            document.setMargins(48).setTitle("Agreement");
            document.add(new Paragraph("SERVICE AGREEMENT").bold().setFontSize(20)
                .setTextAlignment(TextAlignment.CENTER).setFontColor(INK));
            for (int section = 1; section <= 6; section++) {
                document.add(new Paragraph(section + ". Responsibilities").bold().setMarginTop(10));
                document.add(new Paragraph("The parties deliver the services described in the statement of work.")
                    .setFontSize(10).setMultipliedLeading(1.25f));
            }
            document.add(new Paragraph("Signatures").bold().setMarginTop(18));
        });
    }

    private static byte[] minutes() {
        return Pdf.create(document -> {
            document.setMargins(44).setTitle("Board minutes");
            document.add(new Paragraph("Board minutes 12.08.2026").bold().setFontSize(18));
            document.add(new Paragraph("Resolved: to adopt the 2025 annual accounts.").setMarginTop(10));
        });
    }

    private static byte[] auditLetter() {
        return Pdf.create(document -> {
            document.setMargins(44).setTitle("Management letter");
            document.add(new Paragraph("Management letter").bold().setFontSize(18));
            document.add(new Paragraph("Finding 1. Cut-off testing identified one late supplier invoice.")
                .setMarginTop(10));
        });
    }

    private static byte[] paymentAdvice() {
        return Pdf.create(document -> {
            document.setMargins(36).setTitle("Payment advice");
            document.add(new Paragraph("Payment advice").bold().setFontSize(18));
            document.add(new Paragraph("Remitted 12.08.2026 · NOK 15 625.00 · Faktura 1001"));
        });
    }

    private static byte[] reminderEn() {
        return Pdf.create(document -> {
            document.setMargins(40).setTitle("Payment reminder");
            document.add(new Paragraph("Payment reminder").bold().setFontSize(20).setFontColor(ACCENT));
            document.add(new Paragraph("Invoice 2026-1001 remains unpaid. Interest may be added."));
        });
    }

    private static Table table(float[] widths, String... headers) {
        var table = new Table(UnitValue.createPercentArray(widths)).useAllAvailableWidth();
        for (var header : headers) {
            table.addHeaderCell(new Cell().add(new Paragraph(header).bold().setFontSize(8))
                .setBackgroundColor(ACCENT).setFontColor(ColorConstants.WHITE)
                .setBorder(new SolidBorder(ACCENT, 0.4f)).setPadding(4));
        }
        return table;
    }

    private static void row(Table table, String... values) {
        for (var value : values) {
            table.addCell(new Cell().add(new Paragraph(value).setFontSize(8))
                .setBorder(new SolidBorder(ColorConstants.LIGHT_GRAY, 0.3f)).setPadding(3));
        }
    }

    public record NamedDocument(String name, String title, List<String> expectedText, Supplier<byte[]> generator) {
    }
}
