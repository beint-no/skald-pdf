package org.skaldpdf.reai;

import org.skaldpdf.Pdf;
import org.skaldpdf.barcode.QrCode;
import org.skaldpdf.colors.DeviceRgb;
import org.skaldpdf.font.PdfFont;
import org.skaldpdf.font.PdfFontFactory;
import org.skaldpdf.image.ImageDataFactory;
import org.skaldpdf.layout.Document;
import org.skaldpdf.layout.borders.Border;
import org.skaldpdf.layout.borders.SolidBorder;
import org.skaldpdf.layout.canvas.SolidLine;
import org.skaldpdf.layout.element.Cell;
import org.skaldpdf.layout.element.Image;
import org.skaldpdf.layout.element.LineSeparator;
import org.skaldpdf.layout.element.Paragraph;
import org.skaldpdf.layout.element.Table;
import org.skaldpdf.layout.properties.HorizontalAlignment;
import org.skaldpdf.layout.properties.TextAlignment;
import org.skaldpdf.layout.properties.UnitValue;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Faithful Skald reconstruction of ReAI's iText invoice family
 * ({@code InvoicePdfGenerator}, {@code OrderPdfGenerator},
 * {@code InvoiceReminderPdfGenerator}). Labels, 40 pt A4 margins, right-aligned
 * company block, 2 pt rules, and 7/8-column line tables follow the ReAI source.
 *
 * <p>Visual difference that is intentional: ReAI embeds nothing and names
 * Helvetica. Skald embeds a compact IBM Plex / Skald Sans subset (PDF 2.0).
 */
public final class ReaiStyleDocuments {
    static final float FONT_SIZE_NORMAL = 10f;
    static final float FONT_SIZE_SMALL = 9f;
    static final float FONT_SIZE_HEADER = 14f;
    static final DeviceRgb BRAND_GRAY = new DeviceRgb(150, 150, 150);

    private ReaiStyleDocuments() {
    }

    public static Map<String, byte[]> all(byte[] logo) {
        var documents = new LinkedHashMap<String, byte[]>();
        documents.put("01-faktura", invoice(sampleInvoice(), null));
        documents.put("02-faktura-logo", invoice(sampleInvoice(), logo));
        documents.put("03-faktura-rabatt", invoice(discountInvoice(), logo));
        documents.put("04-kreditnota", invoice(creditNote(), logo));
        documents.put("05-betalt-kopi", invoice(paidCopy(), logo));
        documents.put("06-faktura-preview", invoice(previewInvoice(), logo));
        documents.put("07-faktura-en", invoice(englishInvoice(), logo));
        documents.put("08-faktura-mange-linjer", invoice(longInvoice(), logo));
        documents.put("09-ordrebekreftelse", orderConfirmation(sampleOrder(), logo));
        documents.put("10-purring", reminder(false, logo));
        documents.put("11-betalingsoppfordring", reminder(true, logo));
        documents.put("12-pakkseddel", packingSlip(logo));
        documents.put("13-ehf-forhandsvisning", ehfPreview(logo));
        documents.put("14-faktura-delbetalt", invoice(partiallyPaidInvoice(), logo));
        documents.put("15-faktura-qr", invoiceWithQr(sampleInvoice(), logo));
        return documents;
    }

    public static byte[] invoice(InvoiceModel model, byte[] logo) {
        return create(document -> {
            document.setTitle(model.documentTitle()).setAuthor(model.company().name()).setLanguage(model.language());
            var fonts = fonts();
            addHeader(document, fonts, model.company(), model.labels(), logo);
            addCustomer(document, fonts, model.customer());
            addInvoiceDetails(document, fonts, model);
            if (model.paymentReceipt() != null) {
                addPaymentReceipt(document, fonts, model);
            }
            if (model.showPaymentDetails()) {
                addPaymentDetails(document, fonts, model);
            }
            addDetails(document, fonts, model);
            addLinesAndSummary(document, fonts, model);
            addBranding(document, model.labels().branding());
        });
    }

    public static byte[] invoiceWithQr(InvoiceModel model, byte[] logo) {
        return create(document -> {
            document.setTitle(model.documentTitle()).setAuthor(model.company().name()).setLanguage(model.language());
            var fonts = fonts();
            addHeader(document, fonts, model.company(), model.labels(), logo);
            addCustomer(document, fonts, model.customer());
            addInvoiceDetails(document, fonts, model);
            addPaymentDetails(document, fonts, model);
            addDetails(document, fonts, model);
            addLinesAndSummary(document, fonts, model);
            document.add(new Paragraph("Betaling med QR").bold().setFontSize(FONT_SIZE_NORMAL).setMarginTop(16));
            document.add(new Image(new QrCode(kidPayload(model))).scaleInto(88, 88));
            addBranding(document, model.labels().branding());
        });
    }

    public static byte[] orderConfirmation(InvoiceModel model, byte[] logo) {
        return create(document -> {
            document.setTitle(model.documentTitle()).setAuthor(model.company().name()).setLanguage("nb-NO");
            var fonts = fonts();
            addHeader(document, fonts, model.company(), model.labels(), logo);
            addCustomer(document, fonts, model.customer());
            addInvoiceDetails(document, fonts, model);
            addLinesAndSummary(document, fonts, model);
        });
    }

    public static byte[] reminder(boolean collection, byte[] logo) {
        var model = sampleInvoice();
        var labels = Labels.norwegian(false);
        var title = collection ? "Betalingsoppfordring" : "Purring";
        return create(document -> {
            document.setTitle(title + " for faktura 1001").setAuthor(model.company().name()).setLanguage("nb-NO");
            var fonts = fonts();
            addHeader(document, fonts, model.company(), labels, logo);
            addCustomer(document, fonts, model.customer());
            document.add(new Paragraph(title).bold().setFontSize(18).setMarginTop(12));
            document.add(new Paragraph(title + " for faktura 1001")
                .setFontSize(FONT_SIZE_NORMAL).setMultipliedLeading(1f).setMarginBottom(8));
            document.add(new Paragraph(collection
                ? "Vi ser ikke ut til å ha mottatt betaling for fakturaen nedenfor. De varsles med dette om at skyldig beløp må være betalt innen 14 dager fra datoen for dette varselet. Dersom kravet ikke betales i sin helhet innen fristen, kan kravet bli begjært tvangsinnfordret (utlegg) gjennom namsmyndighetene, jf. tvangsfullbyrdelsesloven §4-18 og §4-19."
                : "Vi ser ikke ut til å ha mottatt betaling for fakturaen under. Dersom betaling skjer etter forfallsdato kan rente- og purregebyr bli lagt til. Dersom betalingen er gjort de siste dagene, vennligst se bort fra denne påminnelsen.")
                .setFontSize(FONT_SIZE_NORMAL).setMultipliedLeading(1.15f));
            var table = new Table(UnitValue.createPercentArray(new float[] {18, 18, 18, 18, 28}))
                .useAllAvailableWidth()
                .setMarginTop(16);
            headerCell(table, "Fakturanr.");
            headerCell(table, "Fakturadato");
            headerCell(table, "Forfallsdato");
            headerCell(table, "Beløp");
            headerCell(table, "Beskrivelse / Spesifikasjon");
            addPlain(table, "1001", TextAlignment.LEFT);
            addPlain(table, "12.08.2026", TextAlignment.LEFT);
            addPlain(table, "26.08.2026", TextAlignment.LEFT);
            addPlain(table, "NOK 15,625.00", TextAlignment.RIGHT);
            addPlain(table, "Opprinnelig faktura", TextAlignment.LEFT);
            addPlain(table, "", TextAlignment.LEFT);
            addPlain(table, "09.09.2026", TextAlignment.LEFT);
            addPlain(table, "", TextAlignment.LEFT);
            addPlain(table, "70.00", TextAlignment.RIGHT);
            addPlain(table, "Purregebyr", TextAlignment.LEFT);
            addPlain(table, "", TextAlignment.LEFT);
            addPlain(table, "09.09.2026", TextAlignment.LEFT);
            addPlain(table, "", TextAlignment.LEFT);
            addPlain(table, "82.47", TextAlignment.RIGHT);
            addPlain(table, "Renter 12.00 % · 14 rentedager", TextAlignment.LEFT);
            table.addCell(empty(3).setBorderTop(new SolidBorder(2f)));
            table.addCell(cell("Til betaling", true, FONT_SIZE_SMALL, TextAlignment.LEFT));
            table.addCell(cell("NOK 15,777.47", true, FONT_SIZE_SMALL, TextAlignment.RIGHT));
            document.add(table);
            addBranding(document, labels.branding());
        });
    }

    public static byte[] packingSlip(byte[] logo) {
        var model = sampleInvoice();
        return create(document -> {
            document.setTitle("Pakkseddel 1001").setAuthor(model.company().name()).setLanguage("nb-NO");
            var fonts = fonts();
            addHeader(document, fonts, model.company(), Labels.norwegian(false), logo);
            addCustomer(document, fonts, model.customer());
            document.add(new Paragraph("Pakkseddel").bold().setFontSize(18).setMarginTop(10));
            var meta = new Table(new float[] {1, 1})
                .setWidth(UnitValue.createPercentValue(50f))
                .setHorizontalAlignment(HorizontalAlignment.RIGHT)
                .setBorder(Border.NO_BORDER);
            meta.addCell(cell("Ordrenr.:", false, FONT_SIZE_NORMAL, TextAlignment.LEFT));
            meta.addCell(cell("1001", false, FONT_SIZE_NORMAL, TextAlignment.RIGHT));
            meta.addCell(cell("Leveringsdato:", false, FONT_SIZE_NORMAL, TextAlignment.LEFT));
            meta.addCell(cell("14.08.2026", false, FONT_SIZE_NORMAL, TextAlignment.RIGHT));
            meta.addCell(cell("Sporing:", false, FONT_SIZE_NORMAL, TextAlignment.LEFT));
            meta.addCell(cell("POSTEN 373724189NO", false, FONT_SIZE_NORMAL, TextAlignment.RIGHT));
            document.add(meta);
            document.add(new Paragraph("Vennligst sjekk innholdet mot listen under.")
                .setFontSize(FONT_SIZE_NORMAL).setMarginTop(16));
            var table = new Table(UnitValue.createPercentArray(new float[] {38, 22, 12, 14, 14}))
                .useAllAvailableWidth()
                .setMarginTop(16);
            headerCell(table, "Vare");
            headerCell(table, "SKU");
            headerCell(table, "Antall");
            headerCell(table, "Lokasjon");
            headerCell(table, "Pakket");
            addPlain(table, "Regnskapstjeneste august", "REG-AUG", "8", "A-12", "☐");
            addPlain(table, "Lønnskjøring", "PAY-2026-08", "1", "A-12", "☐");
            addPlain(table, "Årsoppgjør tillegg", "YEAR-ADD", "1", "B-04", "☐");
            document.add(table);
            document.add(new Image(new QrCode("https://sporing.posten.no/373724189NO")).scaleInto(72, 72)
                .setMarginTop(18));
            addBranding(document, "Denne pakkseddelen er laget med Skald, samme layoutmotor som ReAI.");
        });
    }

    public static byte[] ehfPreview(byte[] logo) {
        return create(document -> {
            document.setTitle("EHF Faktura 1001").setAuthor("Nordlys Handel AS").setLanguage("nb-NO");
            document.setWatermark("EHF FORHÅNDSVISNING");
            var fonts = fonts();
            addHeader(document, fonts, Company.nordlys(), Labels.norwegian(false), logo);
            addCustomer(document, fonts, Customer.fjordbutikken());
            addInvoiceDetails(document, fonts, sampleInvoice());
            addLinesAndSummary(document, fonts, sampleInvoice());
            addBranding(document, Labels.norwegian(false).branding());
        });
    }

    public static InvoiceModel sampleInvoice() {
        return InvoiceModel.norwegian("1001", "Faktura", false, false, false, List.of(
            line("Regnskapstjeneste august", "Løpende avtale", "8", "1,250.00", null, "10,000.00", "25", "12,500.00"),
            line("Lønnskjøring", "", "1", "2,500.00", null, "2,500.00", "25", "3,125.00")
        ), totals("12,500.00", "3,125.00", "15,625.00"));
    }

    public static InvoiceModel discountInvoice() {
        return InvoiceModel.norwegian("1002", "Faktura", true, false, false, List.of(
            line("Konsulenttimer", "Avtalt rabatt", "10", "1,250.00", "10 %", "11,250.00", "25", "14,062.50")
        ), totals("12,500.00", "3,125.00", "14,062.50"));
    }

    public static InvoiceModel creditNote() {
        return InvoiceModel.norwegian("9001", "Kreditnota", false, true, false, List.of(
            line("Regnskapstjeneste august", "Kreditert", "8", "-1,250.00", null, "-10,000.00", "25", "-12,500.00")
        ), totals("-10,000.00", "-2,500.00", "-12,500.00"))
            .withCreditFor("1001", "12.08.2026");
    }

    public static InvoiceModel paidCopy() {
        return InvoiceModel.norwegian("1001", "Betalt fakturakopi", false, false, true, List.of(
            line("Regnskapstjeneste august", "", "8", "1,250.00", null, "10,000.00", "25", "12,500.00"),
            line("Lønnskjøring", "", "1", "2,500.00", null, "2,500.00", "25", "3,125.00")
        ), totals("12,500.00", "3,125.00", "15,625.00"))
            .withPayment("15,625.00", "20.08.2026", true, "0.00");
    }

    public static InvoiceModel previewInvoice() {
        return InvoiceModel.norwegian("preview", "Faktura Forhåndsvisning", false, false, false, List.of(
            line("Utkast linje", "", "1", "1,000.00", null, "1,000.00", "25", "1,250.00")
        ), totals("1,000.00", "250.00", "1,250.00"));
    }

    public static InvoiceModel englishInvoice() {
        return InvoiceModel.english("1044", List.of(
            line("Monthly accounting", "Retainer", "1", "8,000.00", null, "8,000.00", "25", "10,000.00")
        ), totals("8,000.00", "2,000.00", "10,000.00"));
    }

    public static InvoiceModel longInvoice() {
        var lines = new ArrayList<Line>();
        for (int index = 1; index <= 28; index++) {
            lines.add(line("Modern accounting service " + index, "Periode " + index,
                "1", "1,250.00", null, "1,250.00", "25", "1,562.50"));
        }
        return InvoiceModel.norwegian("1088", "Faktura", false, false, false, lines,
            totals("35,000.00", "8,750.00", "43,750.00"));
    }

    public static InvoiceModel partiallyPaidInvoice() {
        return InvoiceModel.norwegian("1003", "Faktura", false, false, false, List.of(
            line("Regnskapstjeneste august", "", "8", "1,250.00", null, "10,000.00", "25", "12,500.00")
        ), totals("10,000.00", "2,500.00", "12,500.00"))
            .withPayment("5,000.00", "18.08.2026", false, "7,500.00");
    }

    public static InvoiceModel sampleOrder() {
        return InvoiceModel.norwegian("5512", "Ordrebekreftelse", false, false, false, List.of(
            line("Regnskapstjeneste september", "", "8", "1,250.00", null, "10,000.00", "25", "12,500.00")
        ), totals("10,000.00", "2,500.00", "12,500.00"))
            .asOrder();
    }

    static byte[] create(Consumer<Document> content) {
        return Pdf.create(document -> {
            document.setMargins(40, 40, 40, 40);
            content.accept(document);
        });
    }

    private static void addHeader(Document document, Fonts fonts, Company company, Labels labels, byte[] logo) {
        if (logo == null) {
            document.add(new Paragraph(company.name())
                .setFont(fonts.bold)
                .setFontSize(FONT_SIZE_HEADER)
                .setTextAlignment(TextAlignment.RIGHT)
                .setMarginBottom(2));
        } else {
            try {
                var header = new Table(UnitValue.createPercentArray(new float[] {50, 50}))
                    .useAllAvailableWidth()
                    .setBorder(Border.NO_BORDER)
                    .setMarginBottom(2);
                header.addCell(new Cell().setBorder(Border.NO_BORDER).setPadding(0)
                    .add(new Image(ImageDataFactory.create(logo)).scaleToFit(160, 60)));
                header.addCell(new Cell().setBorder(Border.NO_BORDER).setPadding(0)
                    .add(new Paragraph(company.name()).setFont(fonts.bold).setFontSize(FONT_SIZE_HEADER)
                        .setTextAlignment(TextAlignment.RIGHT)));
                document.add(header);
            } catch (Exception exception) {
                throw new IllegalStateException(exception);
            }
        }
        document.add(new LineSeparator(new SolidLine(2f)).setMarginTop(2).setMarginBottom(4));
        document.add(new Paragraph(company.addressLine())
            .setFont(fonts.regular)
            .setFontSize(FONT_SIZE_NORMAL)
            .setWidth(UnitValue.createPercentValue(40f))
            .setTextAlignment(TextAlignment.LEFT)
            .setHorizontalAlignment(HorizontalAlignment.RIGHT)
            .setMarginBottom(0)
            .setMultipliedLeading(1f));
        var orgValue = company.country() + company.organizationNumber() + (company.vatRegistered() ? "MVA" : "");
        var labelWidth = fonts.bold.getWidth(labels.companyNumber(), FONT_SIZE_NORMAL) + 8;
        var valueWidth = fonts.regular.getWidth(orgValue, FONT_SIZE_NORMAL) + 8;
        var org = Table.withColumns(UnitValue.createPointValue(labelWidth), UnitValue.createPointValue(valueWidth))
            .setHorizontalAlignment(HorizontalAlignment.RIGHT)
            .setBorder(Border.NO_BORDER)
            .setMarginTop(10);
        org.addCell(cell(labels.companyNumber(), true, FONT_SIZE_NORMAL, TextAlignment.LEFT));
        org.addCell(cell(orgValue, false, FONT_SIZE_NORMAL, TextAlignment.LEFT));
        document.add(org);
    }

    private static void addCustomer(Document document, Fonts fonts, Customer customer) {
        document.add(new Paragraph(customer.name())
            .setFont(fonts.regular).setFontSize(FONT_SIZE_NORMAL)
            .setMarginTop(18).setMarginBottom(0.2f).setMultipliedLeading(1f));
        for (var line : customer.addressLines()) {
            document.add(new Paragraph(line)
                .setFont(fonts.regular).setFontSize(FONT_SIZE_NORMAL)
                .setMarginBottom(0.2f).setMultipliedLeading(1f));
        }
    }

    private static void addInvoiceDetails(Document document, Fonts fonts, InvoiceModel model) {
        var table = new Table(new float[] {1, 1})
            .setWidth(UnitValue.createPercentValue(50f))
            .setHorizontalAlignment(HorizontalAlignment.RIGHT)
            .setBorder(Border.NO_BORDER);
        table.addCell(new Cell(2).setBorder(Border.NO_BORDER).setPaddingBottom(2)
            .add(new Paragraph(model.title()).setFont(fonts.bold).setFontSize(18)));
        table.addCell(cell(model.labels().numberLabel(), false, FONT_SIZE_NORMAL, TextAlignment.LEFT));
        table.addCell(cell(model.number(), false, FONT_SIZE_NORMAL, TextAlignment.RIGHT));
        table.addCell(cell(model.labels().dateLabel(), false, FONT_SIZE_NORMAL, TextAlignment.LEFT));
        table.addCell(cell(model.issueDate(), false, FONT_SIZE_NORMAL, TextAlignment.RIGHT));
        table.addCell(cell("Vår ref.:", false, FONT_SIZE_NORMAL, TextAlignment.LEFT));
        table.addCell(cell(model.ourReference(), false, FONT_SIZE_NORMAL, TextAlignment.RIGHT));
        table.addCell(cell("Deres ref.:", false, FONT_SIZE_NORMAL, TextAlignment.LEFT));
        table.addCell(cell(model.buyerReference(), false, FONT_SIZE_NORMAL, TextAlignment.RIGHT));
        table.addCell(empty(2).setPaddingTop(4));
        document.add(table);
    }

    private static void addPaymentDetails(Document document, Fonts fonts, InvoiceModel model) {
        var table = new Table(new float[] {2, 5})
            .setWidth(UnitValue.createPercentValue(50f))
            .setHorizontalAlignment(HorizontalAlignment.RIGHT)
            .setBorder(Border.NO_BORDER);
        table.addCell(new Cell(2).setBorder(Border.NO_BORDER).setPaddingBottom(2)
            .add(new Paragraph(model.labels().paymentInfo()).setFont(fonts.bold)));
        table.addCell(cell(model.labels().dueDate(), false, FONT_SIZE_NORMAL, TextAlignment.LEFT));
        table.addCell(cell(model.dueDate(), false, FONT_SIZE_NORMAL, TextAlignment.RIGHT));
        table.addCell(cell(model.labels().bankName(), false, FONT_SIZE_NORMAL, TextAlignment.LEFT));
        table.addCell(cell(model.bank().name(), false, FONT_SIZE_NORMAL, TextAlignment.RIGHT));
        table.addCell(cell(model.labels().accountNumber(), false, FONT_SIZE_NORMAL, TextAlignment.LEFT));
        table.addCell(cell(chunked(model.bank().account()), false, FONT_SIZE_NORMAL, TextAlignment.RIGHT));
        table.addCell(cell(model.labels().iban(), false, FONT_SIZE_NORMAL, TextAlignment.LEFT));
        table.addCell(cell(chunked(model.bank().iban()), false, FONT_SIZE_NORMAL, TextAlignment.RIGHT));
        table.addCell(cell("BIC/SWIFT", false, FONT_SIZE_NORMAL, TextAlignment.LEFT));
        table.addCell(cell(model.bank().bic(), false, FONT_SIZE_NORMAL, TextAlignment.RIGHT));
        document.add(table);
    }

    private static void addPaymentReceipt(Document document, Fonts fonts, InvoiceModel model) {
        var receipt = model.paymentReceipt();
        var table = new Table(new float[] {2, 4})
            .setWidth(UnitValue.createPercentValue(50f))
            .setHorizontalAlignment(HorizontalAlignment.RIGHT)
            .setBorder(Border.NO_BORDER)
            .setMarginBottom(8);
        table.addCell(new Cell(2).setBorder(Border.NO_BORDER).setPaddingBottom(2)
            .add(new Paragraph(model.labels().paymentReceipt()).setFont(fonts.bold)));
        table.addCell(cell(model.labels().paidAmount(), false, FONT_SIZE_NORMAL, TextAlignment.LEFT));
        table.addCell(cell("NOK " + receipt.paidAmount(), false, FONT_SIZE_NORMAL, TextAlignment.RIGHT));
        table.addCell(cell(model.labels().lastPaymentDate(), false, FONT_SIZE_NORMAL, TextAlignment.LEFT));
        table.addCell(cell(receipt.lastPaymentDate(), false, FONT_SIZE_NORMAL, TextAlignment.RIGHT));
        if (receipt.paidViaGateway()) {
            table.addCell(cell("Betalt med", false, FONT_SIZE_NORMAL, TextAlignment.LEFT));
            table.addCell(cell("Betalingskort/betalingsløsning", false, FONT_SIZE_NORMAL, TextAlignment.RIGHT));
        }
        document.add(table);
    }

    private static void addDetails(Document document, Fonts fonts, InvoiceModel model) {
        if (model.creditFor() != null) {
            document.add(new Paragraph("Kreditnota for faktura " + model.creditFor().number()
                + " datert " + model.creditFor().date())
                .setFont(fonts.regular).setFontSize(FONT_SIZE_NORMAL)
                .setMarginTop(25).setMarginBottom(0.2f).setMultipliedLeading(1f));
        }
        if (model.showPaymentReference()) {
            document.add(new Paragraph()
                .add(new org.skaldpdf.layout.element.Text("Vennligst oppgi fakturanummer ").setFont(fonts.regular))
                .add(new org.skaldpdf.layout.element.Text(model.number()).setFont(fonts.bold))
                .add(new org.skaldpdf.layout.element.Text(" ved betaling").setFont(fonts.regular))
                .setFontSize(FONT_SIZE_NORMAL)
                .setMultipliedLeading(1f)
                .setMarginTop(20));
        }
    }

    private static void addLinesAndSummary(Document document, Fonts fonts, InvoiceModel model) {
        var showDiscount = model.showDiscount();
        var columns = showDiscount
            ? new float[] {20, 16, 6, 13, 8, 13, 10, 14}
            : new float[] {22, 18, 6, 14, 14, 11, 15};
        var table = new Table(UnitValue.createPercentArray(columns)).useAllAvailableWidth().setMarginTop(40);
        for (int index = 0; index < model.labels().lineHeaders().size(); index++) {
            table.addHeaderCell(cell(model.labels().lineHeaders().get(index), true, FONT_SIZE_SMALL,
                index <= 1 ? TextAlignment.LEFT : TextAlignment.RIGHT)
                .setBorder(Border.NO_BORDER)
                .setBorderBottom(new SolidBorder(0.25f))
                .setPadding(3));
        }
        for (var line : model.lines()) {
            addLine(table, line, showDiscount);
        }
        var headerCount = model.labels().lineHeaders().size();
        table.addCell(empty(headerCount).setBorderTop(new SolidBorder(2f)).setHeight(1));
        if (model.lines().size() > 1) {
            table.addCell(cell(model.labels().sum(), true, FONT_SIZE_SMALL, TextAlignment.LEFT, headerCount - 3));
            table.addCell(cell(model.totals().excl(), false, FONT_SIZE_SMALL, TextAlignment.RIGHT));
            table.addCell(cell(model.totals().vat(), false, FONT_SIZE_SMALL, TextAlignment.RIGHT));
            table.addCell(cell(model.totals().incl(), false, FONT_SIZE_SMALL, TextAlignment.RIGHT));
            table.addCell(empty(headerCount).setBorderTop(new SolidBorder(0.25f)));
        }
        table.addCell(cell(model.labels().vat() + " 25 %", false, FONT_SIZE_SMALL, TextAlignment.LEFT, headerCount - 3));
        table.addCell(cell(model.totals().excl(), false, FONT_SIZE_SMALL, TextAlignment.RIGHT));
        table.addCell(cell(model.totals().vat(), false, FONT_SIZE_SMALL, TextAlignment.RIGHT));
        table.addCell(cell(model.totals().incl(), false, FONT_SIZE_SMALL, TextAlignment.RIGHT));
        var payableTitle = model.credit() ? model.labels().amount()
            : model.paymentReceipt() != null ? model.labels().invoiceAmount()
            : model.labels().payable();
        table.addCell(cell(payableTitle, true, FONT_SIZE_SMALL, TextAlignment.LEFT, headerCount - 2));
        table.addCell(cell("NOK " + model.totals().incl(), true, FONT_SIZE_SMALL, TextAlignment.RIGHT, 2));
        if (model.paymentReceipt() != null) {
            table.addCell(cell(model.labels().paidAmount(), false, FONT_SIZE_SMALL, TextAlignment.LEFT, headerCount - 2));
            table.addCell(cell("NOK " + model.paymentReceipt().paidAmount(), false, FONT_SIZE_SMALL, TextAlignment.RIGHT, 2));
            table.addCell(cell(model.labels().outstanding(), true, FONT_SIZE_SMALL, TextAlignment.LEFT, headerCount - 2));
            table.addCell(cell("NOK " + model.paymentReceipt().outstanding(), true, FONT_SIZE_SMALL, TextAlignment.RIGHT, 2));
        }
        table.addCell(empty(headerCount).setBorderTop(new SolidBorder(2f)).setHeight(1));
        document.add(table);
    }

    private static void addLine(Table table, Line line, boolean showDiscount) {
        table.addCell(cell(line.name(), false, FONT_SIZE_SMALL, TextAlignment.LEFT));
        table.addCell(cell(line.comment(), false, FONT_SIZE_SMALL, TextAlignment.LEFT));
        table.addCell(cell(line.quantity(), false, FONT_SIZE_SMALL, TextAlignment.RIGHT));
        table.addCell(cell(line.unitPrice(), false, FONT_SIZE_SMALL, TextAlignment.RIGHT));
        if (showDiscount) {
            table.addCell(cell(line.discount() == null ? "0 %" : line.discount(), false, FONT_SIZE_SMALL, TextAlignment.RIGHT));
        }
        table.addCell(cell(line.excl(), false, FONT_SIZE_SMALL, TextAlignment.RIGHT));
        table.addCell(cell(line.vatRate() + " %", false, FONT_SIZE_SMALL, TextAlignment.RIGHT));
        table.addCell(cell(line.incl(), false, FONT_SIZE_SMALL, TextAlignment.RIGHT));
    }

    private static void addBranding(Document document, String text) {
        document.add(new Paragraph(text)
            .setFontSize(FONT_SIZE_SMALL)
            .setFontColor(BRAND_GRAY)
            .setTextAlignment(TextAlignment.CENTER)
            .setMarginTop(30));
    }

    private static void headerCell(Table table, String text) {
        table.addHeaderCell(cell(text, true, FONT_SIZE_SMALL, TextAlignment.LEFT)
            .setBorder(Border.NO_BORDER)
            .setBorderBottom(new SolidBorder(0.25f))
            .setPadding(3));
    }

    private static void addPlain(Table table, String... values) {
        for (var value : values) {
            addPlain(table, value, TextAlignment.LEFT);
        }
    }

    private static void addPlain(Table table, String value, TextAlignment alignment) {
        table.addCell(cell(value, false, FONT_SIZE_SMALL, alignment)
            .setBorder(Border.NO_BORDER)
            .setBorderBottom(new SolidBorder(0.25f)));
    }

    private static Cell cell(String text, boolean bold, float size, TextAlignment alignment) {
        return cell(text, bold, size, alignment, 1);
    }

    private static Cell cell(String text, boolean bold, float size, TextAlignment alignment, int colspan) {
        var paragraph = new Paragraph(text).setFontSize(size).setMultipliedLeading(1f);
        if (bold) {
            paragraph.bold();
        }
        return new Cell(colspan).add(paragraph)
            .setTextAlignment(alignment)
            .setBorder(Border.NO_BORDER)
            .setPadding(1);
    }

    private static Cell empty(int colspan) {
        return new Cell(colspan).setBorder(Border.NO_BORDER);
    }

    private static Fonts fonts() {
        return new Fonts(PdfFontFactory.regular(), PdfFontFactory.bold());
    }

    private static String chunked(String value) {
        var compact = value.replace(" ", "");
        var result = new StringBuilder();
        for (int index = 0; index < compact.length(); index++) {
            if (index > 0 && index % 4 == 0) {
                result.append(' ');
            }
            result.append(compact.charAt(index));
        }
        return result.toString();
    }

    private static String kidPayload(InvoiceModel model) {
        return "SPD*1.0*ACC:" + model.bank().iban()
            + "*AM:" + model.totals().incl().replace(",", "")
            + "*CC:NOK*MSG:Faktura " + model.number();
    }

    private static Line line(String name, String comment, String quantity, String unit, String discount,
                             String excl, String vat, String incl) {
        return new Line(name, comment, quantity, unit, discount, excl, vat, incl);
    }

    private static Totals totals(String excl, String vat, String incl) {
        return new Totals(excl, vat, incl);
    }

    public static String formatMoney(BigDecimal amount) {
        var rounded = amount.setScale(2, RoundingMode.HALF_UP);
        var pattern = rounded.stripTrailingZeros().scale() <= 0 ? "#,##0" : "#,##0.00";
        var format = new DecimalFormat(pattern, DecimalFormatSymbols.getInstance(Locale.US));
        format.setRoundingMode(RoundingMode.HALF_UP);
        return format.format(rounded);
    }

    record Fonts(PdfFont regular, PdfFont bold) {
    }

    public record Company(String name, String country, String organizationNumber, String addressLine,
                          boolean vatRegistered) {
        static Company nordlys() {
            return new Company("Nordlys Handel AS", "NO", "999888777",
                "Storgata 10, 0184 Oslo, Norge", true);
        }
    }

    public record Customer(String name, List<String> addressLines) {
        static Customer fjordbutikken() {
            return new Customer("Fjordbutikken AS", List.of("Kaien 4", "5003 Bergen"));
        }
    }

    public record Bank(String name, String account, String iban, String bic) {
        static Bank dnb() {
            return new Bank("DNB Bank ASA", "15034567890", "NO9315034567890", "DNBANOKK");
        }
    }

    public record Line(String name, String comment, String quantity, String unitPrice, String discount,
                       String excl, String vatRate, String incl) {
    }

    public record Totals(String excl, String vat, String incl) {
    }

    public record PaymentReceipt(String paidAmount, String lastPaymentDate, boolean paidViaGateway,
                                 String outstanding) {
    }

    public record CreditFor(String number, String date) {
    }

    public record Labels(
        List<String> lineHeaders,
        String companyNumber,
        String numberLabel,
        String dateLabel,
        String paymentInfo,
        String dueDate,
        String bankName,
        String accountNumber,
        String iban,
        String sum,
        String vat,
        String payable,
        String amount,
        String invoiceAmount,
        String paidAmount,
        String outstanding,
        String paymentReceipt,
        String lastPaymentDate,
        String branding
    ) {
        static Labels norwegian(boolean discount) {
            var headers = new ArrayList<String>();
            headers.add("Beskrivelse");
            headers.add("Kommentar");
            headers.add("Antall");
            headers.add("Enhetspris\n(eks. MVA)");
            if (discount) {
                headers.add("Rabatt");
            }
            headers.add("Beløp\n(eks. MVA)");
            headers.add("MVA");
            headers.add("Beløp\n(inkl. MVA)");
            return new Labels(List.copyOf(headers), "Organisasjonsnummer:", "Fakturanr.:", "Fakturadato:",
                "Betalingsinformasjon", "Forfallsdato:", "Banknavn:", "Kontonummer", "IBAN",
                "Sum", "MVA-spesifikasjon", "Til betaling", "Beløp", "Fakturabeløp", "Betalt beløp",
                "Utestående beløp", "Betalingskvittering", "Siste betalingsdato",
                "Denne fakturaen er sendt med ReAI, et rimeligere og mer effektivt regnskapssystem: reai.no");
        }

        static Labels english(boolean discount) {
            var headers = new ArrayList<String>();
            headers.add("Description");
            headers.add("Comment");
            headers.add("Quantity");
            headers.add("Unit Price\n(excl. VAT)");
            if (discount) {
                headers.add("Discount");
            }
            headers.add("Amount\n(excl. VAT)");
            headers.add("VAT");
            headers.add("Amount\n(incl. VAT)");
            return new Labels(List.copyOf(headers), "Company Number:", "Invoice No.:", "Invoice Date:",
                "Payment Information", "Due Date:", "Bank Name:", "Account Number", "IBAN",
                "Sum", "VAT specification", "Payable", "Amount", "Invoice Amount", "Paid Amount",
                "Outstanding Amount", "Payment Receipt", "Last Payment Date",
                "This invoice was sent with ReAI, a more affordable and efficient accounting system: reai.no");
        }

        static Labels order() {
            return new Labels(norwegian(false).lineHeaders(), "Organisasjonsnummer:", "Ordrenr.:", "Ordredato:",
                "Betalingsinformasjon", "Forfallsdato:", "Banknavn:", "Kontonummer", "IBAN",
                "Sum", "MVA-spesifikasjon", "Sum", "Beløp", "Fakturabeløp", "Betalt beløp",
                "Utestående beløp", "Betalingskvittering", "Siste betalingsdato",
                norwegian(false).branding());
        }
    }

    public static final class InvoiceModel {
        private final Company company;
        private final Customer customer;
        private final Bank bank;
        private final Labels labels;
        private final String number;
        private final String title;
        private final String issueDate;
        private final String dueDate;
        private final String ourReference;
        private final String buyerReference;
        private final String language;
        private final boolean showDiscount;
        private final boolean credit;
        private final boolean showPaymentDetails;
        private final List<Line> lines;
        private final Totals totals;
        private PaymentReceipt paymentReceipt;
        private CreditFor creditFor;
        private boolean order;

        InvoiceModel(Company company, Customer customer, Bank bank, Labels labels, String number, String title,
                     String issueDate, String dueDate, String ourReference, String buyerReference, String language,
                     boolean showDiscount, boolean credit, boolean showPaymentDetails, List<Line> lines, Totals totals) {
            this.company = company;
            this.customer = customer;
            this.bank = bank;
            this.labels = labels;
            this.number = number;
            this.title = title;
            this.issueDate = issueDate;
            this.dueDate = dueDate;
            this.ourReference = ourReference;
            this.buyerReference = buyerReference;
            this.language = language;
            this.showDiscount = showDiscount;
            this.credit = credit;
            this.showPaymentDetails = showPaymentDetails;
            this.lines = List.copyOf(lines);
            this.totals = totals;
        }

        static InvoiceModel norwegian(String number, String title, boolean discount, boolean credit, boolean paid,
                                      List<Line> lines, Totals totals) {
            var showPayment = !credit && !paid;
            return new InvoiceModel(Company.nordlys(), Customer.fjordbutikken(), Bank.dnb(),
                Labels.norwegian(discount), number, title, "12.08.2026", "26.08.2026",
                "Kari Nord", "Ola Fjord", "nb-NO", discount, credit, showPayment, lines, totals);
        }

        static InvoiceModel english(String number, List<Line> lines, Totals totals) {
            return new InvoiceModel(Company.nordlys(), Customer.fjordbutikken(), Bank.dnb(),
                Labels.english(false), number, "Invoice", "12.08.2026", "26.08.2026",
                "Kari Nord", "Ola Fjord", "en", false, false, true, lines, totals);
        }

        InvoiceModel withPayment(String paid, String date, boolean gateway, String outstanding) {
            this.paymentReceipt = new PaymentReceipt(paid, date, gateway, outstanding);
            return this;
        }

        InvoiceModel withCreditFor(String number, String date) {
            this.creditFor = new CreditFor(number, date);
            return this;
        }

        InvoiceModel asOrder() {
            this.order = true;
            return this;
        }

        Company company() {
            return company;
        }

        Customer customer() {
            return customer;
        }

        Bank bank() {
            return bank;
        }

        Labels labels() {
            return order ? Labels.order() : labels;
        }

        String number() {
            return number;
        }

        String title() {
            return title;
        }

        String documentTitle() {
            return title + " " + number;
        }

        String issueDate() {
            return issueDate;
        }

        String dueDate() {
            return dueDate;
        }

        String ourReference() {
            return ourReference;
        }

        String buyerReference() {
            return buyerReference;
        }

        String language() {
            return language;
        }

        boolean showDiscount() {
            return showDiscount;
        }

        boolean credit() {
            return credit;
        }

        boolean showPaymentDetails() {
            return showPaymentDetails && paymentReceipt == null;
        }

        boolean showPaymentReference() {
            return showPaymentDetails && !credit && paymentReceipt == null;
        }

        List<Line> lines() {
            return lines;
        }

        Totals totals() {
            return totals;
        }

        PaymentReceipt paymentReceipt() {
            return paymentReceipt;
        }

        CreditFor creditFor() {
            return creditFor;
        }
    }
}
