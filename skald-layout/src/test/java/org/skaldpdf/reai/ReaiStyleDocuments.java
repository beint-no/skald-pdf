package org.skaldpdf.reai;

import org.skaldpdf.invoice.no.LineItem;
import org.skaldpdf.invoice.no.NorwegianInvoice;
import org.skaldpdf.invoice.no.NorwegianMoney;
import org.skaldpdf.packing.no.NorwegianPackingSlip;
import org.skaldpdf.reminder.no.NorwegianReminder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
        documents.put("17-respiro-rf41202600033", invoice(respiroPaidCopy(), logo));
        return documents;
    }

    public static byte[] invoice(InvoiceModel model, byte[] logo) {
        return NorwegianInvoice.pdf(invoiceBuilder(model, logo, false).build());
    }

    public static byte[] invoiceWithQr(InvoiceModel model, byte[] logo) {
        return NorwegianInvoice.pdf(invoiceBuilder(model, logo, true).build());
    }

    public static byte[] orderConfirmation(InvoiceModel model, byte[] logo) {
        return NorwegianInvoice.pdf(invoiceBuilder(model, logo, false)
            .kind(NorwegianInvoice.Kind.ORDER_CONFIRMATION)
            .build());
    }

    public static byte[] reminder(boolean collection, byte[] logo) {
        var sample = sampleInvoice();
        return NorwegianReminder.pdf(NorwegianReminder.Model.builder()
            .kind(collection ? NorwegianReminder.Kind.COLLECTION : NorwegianReminder.Kind.REMINDER)
            .company(toCompany(sample.company()))
            .customer(toParty(sample.customer()))
            .invoiceNumber("1001")
            .invoiceDate(LocalDate.of(2026, 8, 12))
            .dueDate(LocalDate.of(2026, 8, 26))
            .noticeDate(LocalDate.of(2026, 9, 9))
            .originalAmount("15,625.00")
            .lateFee("70.00")
            .interest("82.47", "12", 14)
            .footer(Labels.norwegian(false).branding())
            .logo(logo)
            .build());
    }

    public static byte[] packingSlip(byte[] logo) {
        var sample = sampleInvoice();
        return NorwegianPackingSlip.pdf(NorwegianPackingSlip.Model.builder()
            .company(toCompany(sample.company()))
            .recipient(toParty(sample.customer()))
            .number("1001")
            .deliveryDate(LocalDate.of(2026, 8, 14))
            .tracking("POSTEN 373724189NO")
            .trackingUrl("https://sporing.posten.no/373724189NO")
            .footer("Denne pakkseddelen er laget med Skald, samme layoutmotor som ReAI.")
            .logo(logo)
            .line("Regnskapstjeneste august", "REG-AUG", 8, "A-12")
            .line("Lønnskjøring", "PAY-2026-08", 1, "A-12")
            .line("Årsoppgjør tillegg", "YEAR-ADD", 1, "B-04")
            .build());
    }

    public static byte[] ehfPreview(byte[] logo) {
        return NorwegianInvoice.pdf(invoiceBuilder(sampleInvoice(), logo, false)
            .watermark("EHF FORHÅNDSVISNING")
            .build());
    }

    public static InvoiceModel respiroPaidCopy() {
        return new InvoiceModel(
            new Company("Respiro As", "NO", "922989451", "Almeveien 28, 0855, Oslo, Norge", true),
            new Customer("Famme As", List.of("Søndre Kullerød 8", "3241 Sandefjord")),
            Bank.dnb(),
            Labels.norwegian(false),
            "RF41202600033",
            "Betalt fakturakopi",
            "2026-07-02",
            "2026-07-02",
            "",
            "",
            "nb-NO",
            false,
            false,
            false,
            List.of(
                line("CS AI utvikling", "Mai", "1", "100,000", null, "100,000", "25.0", "125,000"),
                line("CS AI utvikling", "Juni", "1", "100,000", null, "100,000", "25.0", "125,000")
            ),
            totals("200,000", "50,000", "250,000")
        ).withPayment("250,000", "2026-07-02", false, "0");
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

    private static NorwegianInvoice.Builder invoiceBuilder(InvoiceModel model, byte[] logo, boolean paymentQr) {
        var builder = NorwegianInvoice.Model.builder()
            .kind(kindOf(model))
            .language(model.language() != null && model.language().startsWith("en")
                ? NorwegianInvoice.Language.EN : NorwegianInvoice.Language.NB)
            .company(toCompany(model.company()))
            .customer(toParty(model.customer()))
            .bank(toBank(model.bank()))
            .number(model.number())
            .title(model.title())
            .issueDate(parseDate(model.issueDate()))
            .dueDate(parseDate(model.dueDate()))
            .ourReference(model.ourReference())
            .buyerReference(model.buyerReference())
            .footer(model.labels().branding())
            .logo(logo)
            .paymentQr(paymentQr);
        if (model.title() != null && model.title().toLowerCase().contains("forhåndsvisning")) {
            builder.watermark("FORHÅNDSVISNING");
        }
        for (var line : model.lines()) {
            var discount = line.discount() == null || line.discount().isBlank()
                ? null : NorwegianMoney.parse(line.discount().replace("%", "").strip());
            builder.line(new LineItem(line.name(), line.comment(),
                new java.math.BigDecimal(line.quantity().replace(" ", "").replace(",", "")),
                NorwegianMoney.parse(line.unitPrice()),
                discount,
                new java.math.BigDecimal(line.vatRate())));
        }
        if (model.paymentReceipt() != null) {
            builder.paid(model.paymentReceipt().paidAmount(),
                parseDate(model.paymentReceipt().lastPaymentDate()),
                model.paymentReceipt().paidViaGateway());
        }
        if (model.creditFor() != null) {
            builder.creditFor(model.creditFor().number(), parseDate(model.creditFor().date()));
        }
        return builder;
    }

    private static NorwegianInvoice.Kind kindOf(InvoiceModel model) {
        if (model.credit() || model.title().contains("Kreditnota") || model.title().contains("Credit")) {
            return NorwegianInvoice.Kind.CREDIT_NOTE;
        }
        if (model.title().contains("Betalt") || model.title().contains("Paid")) {
            return NorwegianInvoice.Kind.PAID_COPY;
        }
        if (model.title().contains("Ordre") || model.title().contains("Order")) {
            return NorwegianInvoice.Kind.ORDER_CONFIRMATION;
        }
        return NorwegianInvoice.Kind.INVOICE;
    }

    private static org.skaldpdf.invoice.no.Company toCompany(Company company) {
        return new org.skaldpdf.invoice.no.Company(company.name(), company.country(),
            company.organizationNumber(), company.addressLine(), company.vatRegistered());
    }

    private static org.skaldpdf.invoice.no.Party toParty(Customer customer) {
        return new org.skaldpdf.invoice.no.Party(customer.name(), customer.addressLines());
    }

    private static org.skaldpdf.invoice.no.Bank toBank(Bank bank) {
        return new org.skaldpdf.invoice.no.Bank(bank.name(), bank.account(), bank.iban(), bank.bic());
    }

    private static LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        if (value.contains("-")) {
            return LocalDate.parse(value);
        }
        return LocalDate.parse(value, DateTimeFormatter.ofPattern("dd.MM.yyyy"));
    }

    private static Line line(String name, String comment, String quantity, String unit, String discount,
                             String excl, String vat, String incl) {
        return new Line(name, comment, quantity, unit, discount, excl, vat, incl);
    }

    private static Totals totals(String excl, String vat, String incl) {
        return new Totals(excl, vat, incl);
    }

    public static String formatMoney(BigDecimal amount) {
        return NorwegianMoney.format(amount);
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
