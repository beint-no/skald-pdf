package org.skaldpdf.invoice.no;

import org.skaldpdf.barcode.QrCode;
import org.skaldpdf.layout.Document;
import org.skaldpdf.layout.element.Image;
import org.skaldpdf.layout.element.Paragraph;
import org.skaldpdf.layout.element.Table;
import org.skaldpdf.layout.element.Text;
import org.skaldpdf.layout.properties.TextAlignment;
import org.skaldpdf.layout.properties.UnitValue;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;

/**
 * Opinionated Norwegian commercial invoice, based on ReAI's invoice family.
 *
 * <p>This is a named theme — 40 pt A4 margins, right-aligned letterhead,
 * 7- or 8-column VAT line table, KID-style payment block — not a claim that
 * Skald owns every Norwegian bookkeeping layout. Copy it if you need
 * different chrome.
 *
 * <p>Kinds share one table and letterhead: faktura, kreditnota, betalt kopi,
 * tilbud, ordrebekreftelse, and proforma.
 */
public final class NorwegianInvoice {
    private NorwegianInvoice() {
    }

    public enum Kind {
        INVOICE,
        CREDIT_NOTE,
        PAID_COPY,
        QUOTE,
        ORDER_CONFIRMATION,
        PROFORMA
    }

    public enum Language {
        NB,
        EN
    }

    public record Payment(BigDecimal paidAmount, LocalDate lastPaymentDate, boolean paidViaGateway) {
        public Payment {
            paidAmount = NorwegianMoney.amount(paidAmount);
            Objects.requireNonNull(lastPaymentDate, "lastPaymentDate");
        }
    }

    public record CreditFor(String invoiceNumber, LocalDate invoiceDate) {
        public CreditFor {
            invoiceNumber = Company.requireText(invoiceNumber, "invoiceNumber");
            Objects.requireNonNull(invoiceDate, "invoiceDate");
        }
    }

    public record Totals(BigDecimal exVat, BigDecimal vat, BigDecimal incVat) {
        public Totals {
            exVat = NorwegianMoney.amount(exVat);
            vat = NorwegianMoney.amount(vat);
            incVat = NorwegianMoney.amount(incVat);
        }

        public static Totals of(List<LineItem> lines) {
            var ex = BigDecimal.ZERO;
            var vat = BigDecimal.ZERO;
            for (var line : lines) {
                ex = ex.add(line.amountExVat());
                vat = vat.add(line.vatAmount());
            }
            return new Totals(ex, vat, ex.add(vat));
        }
    }

    public static byte[] pdf(Model model) {
        Objects.requireNonNull(model, "model");
        return NorwegianTheme.create(document -> render(document, model));
    }

    public static void write(Path path, Model model) {
        Objects.requireNonNull(model, "model");
        NorwegianTheme.write(path, document -> render(document, model));
    }

    private static void render(Document document, Model model) {
        var copy = Copy.of(model.kind(), model.language());
        NorwegianTheme.metadata(document, model.documentTitle(), model.company().name(), copy.locale);
        if (model.watermark() != null) {
            document.setWatermark(model.watermark());
        }
        NorwegianTheme.header(document, model.company(), copy.companyNumber, model.logo());
        NorwegianTheme.party(document, model.customer());
        addInvoiceDetails(document, model, copy);
        if (model.payment() != null) {
            addPaymentReceipt(document, model, copy);
        }
        if (model.showPaymentDetails()) {
            addPaymentDetails(document, model, copy);
        }
        addNotes(document, model, copy);
        addLinesAndSummary(document, model, copy);
        if (model.paymentQr()) {
            document.add(new Paragraph(copy.qrHeading).bold()
                .setFontSize(NorwegianTheme.FONT_NORMAL).setMarginTop(16));
            document.add(new Image(new QrCode(paymentPayload(model))).scaleInto(88, 88));
        }
        if (model.kind() == Kind.PROFORMA) {
            document.add(new Paragraph(copy.proformaNote)
                .setFontSize(NorwegianTheme.FONT_SMALL)
                .setFontColor(NorwegianTheme.BRAND_GRAY)
                .setMarginTop(12));
        }
        NorwegianTheme.branding(document, model.footer());
    }

    private static void addInvoiceDetails(Document document, Model model, Copy copy) {
        NorwegianTheme.titleBlock(document, model.displayedTitle(),
            copy.numberLabel, model.number(),
            copy.dateLabel, NorwegianMoney.date(model.issueDate()),
            copy.ourRef, model.ourReference(),
            copy.theirRef, model.buyerReference(),
            copy.dueDate, model.kind() == Kind.QUOTE && model.dueDate() != null
                ? NorwegianMoney.date(model.dueDate()) : "");
    }

    private static void addPaymentDetails(Document document, Model model, Copy copy) {
        var bank = model.bank();
        NorwegianTheme.labeledBlock(document, copy.paymentInfo,
            copy.dueDate, NorwegianMoney.date(model.dueDate()),
            copy.bankName, bank == null ? "" : bank.name(),
            copy.accountNumber, bank == null ? "" : NorwegianMoney.chunked(bank.account()),
            copy.iban, bank == null ? "" : NorwegianMoney.chunked(bank.iban()),
            "BIC/SWIFT", bank == null ? "" : bank.bic());
    }

    private static void addPaymentReceipt(Document document, Model model, Copy copy) {
        var payment = model.payment();
        NorwegianTheme.labeledBlock(document, copy.paymentReceipt,
            copy.paidAmount, NorwegianMoney.format(model.currency(), payment.paidAmount()),
            copy.lastPaymentDate, NorwegianMoney.date(payment.lastPaymentDate()),
            copy.paidWith, payment.paidViaGateway() ? copy.paidViaGateway : "");
    }

    private static void addNotes(Document document, Model model, Copy copy) {
        var fonts = NorwegianTheme.fonts();
        if (model.creditFor() != null) {
            document.add(new Paragraph(copy.creditFor(model.creditFor()))
                .setFont(fonts.regular()).setFontSize(NorwegianTheme.FONT_NORMAL)
                .setMarginTop(25).setMarginBottom(0.2f).setMultipliedLeading(1f));
        }
        if (model.showPaymentReference()) {
            document.add(new Paragraph()
                .add(new Text(copy.pleaseQuote).setFont(fonts.regular()))
                .add(new Text(model.number()).setFont(fonts.bold()))
                .add(new Text(copy.whenPaying).setFont(fonts.regular()))
                .setFontSize(NorwegianTheme.FONT_NORMAL)
                .setMultipliedLeading(1f)
                .setMarginTop(20));
        }
        if (model.note() != null && !model.note().isBlank()) {
            document.add(new Paragraph(model.note())
                .setFont(fonts.regular()).setFontSize(NorwegianTheme.FONT_NORMAL)
                .setMarginTop(12).setMultipliedLeading(1.15f));
        }
    }

    private static void addLinesAndSummary(Document document, Model model, Copy copy) {
        var discount = model.showDiscount();
        var columns = discount
            ? new float[] {20, 15, 10, 12, 8, 12, 9, 14}
            : new float[] {22, 16, 10, 13, 13, 10, 16};
        var headers = copy.lineHeaders(discount);
        var table = new Table(UnitValue.createPercentArray(columns)).useAllAvailableWidth().setMarginTop(40);
        for (int index = 0; index < headers.size(); index++) {
            NorwegianTheme.headerCell(table, headers.get(index),
                index <= 1 ? TextAlignment.LEFT : TextAlignment.RIGHT);
        }
        for (var line : model.lines()) {
            addLine(table, line, discount);
        }
        var headerCount = headers.size();
        var totals = model.totals();
        table.addRule(1.25f);
        if (model.lines().size() > 1) {
            table.addCell(NorwegianTheme.cell(copy.sum, true, NorwegianTheme.FONT_SMALL,
                TextAlignment.LEFT, headerCount - 3));
            table.addCell(NorwegianTheme.cell(NorwegianMoney.format(totals.exVat()), false,
                NorwegianTheme.FONT_SMALL, TextAlignment.RIGHT));
            table.addCell(NorwegianTheme.cell(NorwegianMoney.format(totals.vat()), false,
                NorwegianTheme.FONT_SMALL, TextAlignment.RIGHT));
            table.addCell(NorwegianTheme.cell(NorwegianMoney.format(totals.incVat()), false,
                NorwegianTheme.FONT_SMALL, TextAlignment.RIGHT));
            table.addRule(0.4f);
        }
        for (var entry : vatGroups(model.lines()).entrySet()) {
            var group = entry.getValue();
            table.addCell(NorwegianTheme.cell(copy.vat + " " + NorwegianMoney.percent(entry.getKey()),
                false, NorwegianTheme.FONT_SMALL, TextAlignment.LEFT, headerCount - 3));
            table.addCell(NorwegianTheme.cell(NorwegianMoney.format(group.exVat()), false,
                NorwegianTheme.FONT_SMALL, TextAlignment.RIGHT));
            table.addCell(NorwegianTheme.cell(NorwegianMoney.format(group.vat()), false,
                NorwegianTheme.FONT_SMALL, TextAlignment.RIGHT));
            table.addCell(NorwegianTheme.cell(NorwegianMoney.format(group.incVat()), false,
                NorwegianTheme.FONT_SMALL, TextAlignment.RIGHT));
        }
        table.addCell(NorwegianTheme.cell(copy.payableTitle(model), true, NorwegianTheme.FONT_SMALL,
            TextAlignment.LEFT, headerCount - 2));
        table.addCell(NorwegianTheme.cell(NorwegianMoney.format(model.currency(), totals.incVat()), true,
            NorwegianTheme.FONT_SMALL, TextAlignment.RIGHT, 2));
        if (model.payment() != null) {
            table.addCell(NorwegianTheme.cell(copy.paidAmount, false, NorwegianTheme.FONT_SMALL,
                TextAlignment.LEFT, headerCount - 2));
            table.addCell(NorwegianTheme.cell(
                NorwegianMoney.format(model.currency(), model.payment().paidAmount()),
                false, NorwegianTheme.FONT_SMALL, TextAlignment.RIGHT, 2));
            table.addCell(NorwegianTheme.cell(copy.outstanding, true, NorwegianTheme.FONT_SMALL,
                TextAlignment.LEFT, headerCount - 2));
            table.addCell(NorwegianTheme.cell(
                NorwegianMoney.format(model.currency(), model.outstanding()),
                true, NorwegianTheme.FONT_SMALL, TextAlignment.RIGHT, 2));
        }
        table.addRule(1.25f);
        document.add(table);
    }

    private static void addLine(Table table, LineItem line, boolean discount) {
        table.addCell(NorwegianTheme.cell(line.description(), false, NorwegianTheme.FONT_SMALL, TextAlignment.LEFT));
        table.addCell(NorwegianTheme.cell(line.comment(), false, NorwegianTheme.FONT_SMALL, TextAlignment.LEFT));
        table.addCell(NorwegianTheme.cell(NorwegianMoney.quantity(line.quantity()), false,
            NorwegianTheme.FONT_SMALL, TextAlignment.RIGHT));
        table.addCell(NorwegianTheme.cell(NorwegianMoney.format(line.unitPriceExVat()), false,
            NorwegianTheme.FONT_SMALL, TextAlignment.RIGHT));
        if (discount) {
            var shown = line.hasDiscount() ? NorwegianMoney.percent(line.discountPercent()) : "0 %";
            table.addCell(NorwegianTheme.cell(shown, false, NorwegianTheme.FONT_SMALL, TextAlignment.RIGHT));
        }
        table.addCell(NorwegianTheme.cell(NorwegianMoney.format(line.amountExVat()), false,
            NorwegianTheme.FONT_SMALL, TextAlignment.RIGHT));
        table.addCell(NorwegianTheme.cell(NorwegianMoney.percent(line.vatRate()), false,
            NorwegianTheme.FONT_SMALL, TextAlignment.RIGHT));
        table.addCell(NorwegianTheme.cell(NorwegianMoney.format(line.amountIncVat()), false,
            NorwegianTheme.FONT_SMALL, TextAlignment.RIGHT));
    }

    private static LinkedHashMap<BigDecimal, Totals> vatGroups(List<LineItem> lines) {
        var groups = new LinkedHashMap<BigDecimal, List<LineItem>>();
        for (var line : lines) {
            groups.computeIfAbsent(line.vatRate().stripTrailingZeros(), ignored -> new ArrayList<>()).add(line);
        }
        var result = new LinkedHashMap<BigDecimal, Totals>();
        groups.forEach((rate, group) -> result.put(rate, Totals.of(group)));
        return result;
    }

    private static String paymentPayload(Model model) {
        var iban = model.bank() == null ? "" : model.bank().iban();
        return "SPD*1.0*ACC:" + iban
            + "*AM:" + model.totals().incVat().toPlainString()
            + "*CC:" + model.currency()
            + "*MSG:" + model.kind().name() + " " + model.number();
    }

    public static final class Model {
        private final Kind kind;
        private final Language language;
        private final Company company;
        private final Party customer;
        private final Bank bank;
        private final String number;
        private final String titleOverride;
        private final LocalDate issueDate;
        private final LocalDate dueDate;
        private final String ourReference;
        private final String buyerReference;
        private final String currency;
        private final String footer;
        private final String note;
        private final String watermark;
        private final byte[] logo;
        private final boolean paymentQr;
        private final List<LineItem> lines;
        private final Payment payment;
        private final CreditFor creditFor;
        private final Totals totals;

        Model(Builder builder) {
            this.kind = builder.kind;
            this.language = builder.language;
            this.company = Objects.requireNonNull(builder.company, "company");
            this.customer = Objects.requireNonNull(builder.customer, "customer");
            this.bank = builder.bank;
            this.number = Company.requireText(builder.number, "number");
            this.titleOverride = blankToNull(builder.titleOverride);
            this.issueDate = Objects.requireNonNull(builder.issueDate, "issueDate");
            this.dueDate = builder.dueDate;
            this.ourReference = blankToNull(builder.ourReference);
            this.buyerReference = blankToNull(builder.buyerReference);
            this.currency = builder.currency == null || builder.currency.isBlank()
                ? NorwegianMoney.NOK : builder.currency.strip();
            this.footer = blankToNull(builder.footer);
            this.note = blankToNull(builder.note);
            this.watermark = blankToNull(builder.watermark);
            this.logo = builder.logo;
            this.paymentQr = builder.paymentQr;
            this.lines = List.copyOf(builder.lines);
            this.payment = builder.payment;
            this.creditFor = builder.creditFor;
            this.totals = Totals.of(this.lines);
            if (this.lines.isEmpty()) {
                throw new IllegalArgumentException("An invoice needs at least one line");
            }
            if (kind == Kind.CREDIT_NOTE && creditFor == null) {
                throw new IllegalArgumentException("A credit note needs creditFor(invoice, date)");
            }
            if (showPaymentDetails() && dueDate == null) {
                throw new IllegalArgumentException("dueDate is required when payment details are shown");
            }
            if (kind == Kind.QUOTE && dueDate == null) {
                throw new IllegalArgumentException("A quote needs a valid-until date");
            }
            if (paymentQr && (bank == null || bank.iban().isEmpty())) {
                throw new IllegalArgumentException("paymentQr requires a bank IBAN");
            }
        }

        public static Builder builder() {
            return new Builder();
        }

        public Kind kind() {
            return kind;
        }

        public Language language() {
            return language;
        }

        public Company company() {
            return company;
        }

        public Party customer() {
            return customer;
        }

        public Bank bank() {
            return bank;
        }

        public String number() {
            return number;
        }

        public LocalDate issueDate() {
            return issueDate;
        }

        public LocalDate dueDate() {
            return dueDate;
        }

        public String ourReference() {
            return ourReference;
        }

        public String buyerReference() {
            return buyerReference;
        }

        public String currency() {
            return currency;
        }

        public String footer() {
            return footer;
        }

        public String note() {
            return note;
        }

        public String watermark() {
            return watermark;
        }

        public byte[] logo() {
            return logo;
        }

        public boolean paymentQr() {
            return paymentQr;
        }

        public List<LineItem> lines() {
            return lines;
        }

        public Payment payment() {
            return payment;
        }

        public CreditFor creditFor() {
            return creditFor;
        }

        public Totals totals() {
            return totals;
        }

        public String displayedTitle() {
            return titleOverride != null ? titleOverride : Copy.of(kind, language).title;
        }

        public String documentTitle() {
            return displayedTitle() + " " + number;
        }

        public boolean showDiscount() {
            return lines.stream().anyMatch(LineItem::hasDiscount);
        }

        public boolean showPaymentDetails() {
            return (kind == Kind.INVOICE || kind == Kind.PROFORMA) && payment == null;
        }

        public boolean showPaymentReference() {
            return kind == Kind.INVOICE && payment == null;
        }

        public BigDecimal outstanding() {
            if (payment == null) {
                return totals.incVat();
            }
            return NorwegianMoney.amount(totals.incVat().subtract(payment.paidAmount()));
        }

        private static String blankToNull(String value) {
            if (value == null) {
                return null;
            }
            var stripped = value.strip();
            return stripped.isEmpty() ? null : stripped;
        }
    }

    public static final class Builder {
        private Kind kind = Kind.INVOICE;
        private Language language = Language.NB;
        private Company company;
        private Party customer;
        private Bank bank;
        private String number;
        private String titleOverride;
        private LocalDate issueDate;
        private LocalDate dueDate;
        private String ourReference;
        private String buyerReference;
        private String currency = NorwegianMoney.NOK;
        private String footer;
        private String note;
        private String watermark;
        private byte[] logo;
        private boolean paymentQr;
        private final List<LineItem> lines = new ArrayList<>();
        private Payment payment;
        private CreditFor creditFor;

        private Builder() {
        }

        public Builder kind(Kind kind) {
            this.kind = Objects.requireNonNull(kind, "kind");
            return this;
        }

        public Builder language(Language language) {
            this.language = Objects.requireNonNull(language, "language");
            return this;
        }

        public Builder company(Company company) {
            this.company = company;
            return this;
        }

        public Builder customer(Party customer) {
            this.customer = customer;
            return this;
        }

        public Builder bank(Bank bank) {
            this.bank = bank;
            return this;
        }

        public Builder number(String number) {
            this.number = number;
            return this;
        }

        public Builder title(String title) {
            this.titleOverride = title;
            return this;
        }

        public Builder issueDate(LocalDate issueDate) {
            this.issueDate = issueDate;
            return this;
        }

        public Builder dueDate(LocalDate dueDate) {
            this.dueDate = dueDate;
            return this;
        }

        public Builder ourReference(String ourReference) {
            this.ourReference = ourReference;
            return this;
        }

        public Builder buyerReference(String buyerReference) {
            this.buyerReference = buyerReference;
            return this;
        }

        public Builder currency(String currency) {
            this.currency = currency;
            return this;
        }

        public Builder footer(String footer) {
            this.footer = footer;
            return this;
        }

        public Builder note(String note) {
            this.note = note;
            return this;
        }

        public Builder watermark(String watermark) {
            this.watermark = watermark;
            return this;
        }

        public Builder logo(byte[] logo) {
            this.logo = logo;
            return this;
        }

        public Builder paymentQr(boolean paymentQr) {
            this.paymentQr = paymentQr;
            return this;
        }

        public Builder line(LineItem line) {
            lines.add(Objects.requireNonNull(line, "line"));
            return this;
        }

        public Builder line(String description, String comment, BigDecimal quantity,
                            BigDecimal unitPriceExVat, BigDecimal vatRate) {
            return line(new LineItem(description, comment, quantity, unitPriceExVat, vatRate));
        }

        public Builder line(String description, String comment, BigDecimal quantity,
                            BigDecimal unitPriceExVat, BigDecimal discountPercent, BigDecimal vatRate) {
            return line(new LineItem(description, comment, quantity, unitPriceExVat, discountPercent, vatRate));
        }

        public Builder line(String description, String comment, int quantity, String unitPrice, int vatRate) {
            return line(description, comment, BigDecimal.valueOf(quantity),
                NorwegianMoney.parse(unitPrice), BigDecimal.valueOf(vatRate));
        }

        public Builder paid(BigDecimal amount, LocalDate date, boolean paidViaGateway) {
            this.payment = new Payment(amount, date, paidViaGateway);
            return this;
        }

        public Builder paid(String amount, LocalDate date, boolean paidViaGateway) {
            return paid(NorwegianMoney.parse(amount), date, paidViaGateway);
        }

        public Builder creditFor(String invoiceNumber, LocalDate invoiceDate) {
            this.creditFor = new CreditFor(invoiceNumber, invoiceDate);
            return this;
        }

        public Model build() {
            return new Model(this);
        }
    }

    record Copy(
        String locale,
        String title,
        String companyNumber,
        String numberLabel,
        String dateLabel,
        String ourRef,
        String theirRef,
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
        String paidWith,
        String paidViaGateway,
        String pleaseQuote,
        String whenPaying,
        String qrHeading,
        String proformaNote,
        String description,
        String comment,
        String quantity,
        String unitPrice,
        String discount,
        String vatHeader,
        String amountEx,
        String amountInc
    ) {
        static Copy of(Kind kind, Language language) {
            return language == Language.EN ? english(kind) : norwegian(kind);
        }

        private static Copy norwegian(Kind kind) {
            var title = switch (kind) {
                case INVOICE -> "Faktura";
                case CREDIT_NOTE -> "Kreditnota";
                case PAID_COPY -> "Betalt fakturakopi";
                case QUOTE -> "Tilbud";
                case ORDER_CONFIRMATION -> "Ordrebekreftelse";
                case PROFORMA -> "Proforma";
            };
            var numberLabel = switch (kind) {
                case INVOICE, PAID_COPY, PROFORMA -> "Fakturanr.:";
                case CREDIT_NOTE -> "Kreditnotanr.:";
                case QUOTE -> "Tilbudsnr.:";
                case ORDER_CONFIRMATION -> "Ordrenr.:";
            };
            var dateLabel = switch (kind) {
                case INVOICE, PAID_COPY, PROFORMA -> "Fakturadato:";
                case CREDIT_NOTE -> "Dato:";
                case QUOTE -> "Tilbudsdato:";
                case ORDER_CONFIRMATION -> "Ordredato:";
            };
            var due = kind == Kind.QUOTE ? "Gyldig til:" : "Forfallsdato:";
            return new Copy("nb-NO", title, NorwegianTheme.ORG_NUMBER_NB, numberLabel, dateLabel,
                "Vår ref.:", "Deres ref.:", "Betalingsinformasjon", due,
                "Banknavn:", "Kontonummer", "IBAN", "Sum", "MVA-spesifikasjon",
                "Til betaling", "Beløp", "Fakturabeløp", "Betalt beløp", "Utestående beløp",
                "Betalingskvittering", "Siste betalingsdato", "Betalt med",
                "Betalingskort/betalingsløsning", "Vennligst oppgi fakturanummer ", " ved betaling",
                "Betaling med QR", "Dette er ikke en MVA-faktura.",
                "Beskrivelse", "Kommentar", "Antall", "Enhetspris\n(eks. MVA)", "Rabatt", "MVA",
                "Beløp\n(eks. MVA)", "Beløp\n(inkl. MVA)");
        }

        private static Copy english(Kind kind) {
            var title = switch (kind) {
                case INVOICE -> "Invoice";
                case CREDIT_NOTE -> "Credit note";
                case PAID_COPY -> "Paid invoice copy";
                case QUOTE -> "Quote";
                case ORDER_CONFIRMATION -> "Order confirmation";
                case PROFORMA -> "Proforma";
            };
            var numberLabel = switch (kind) {
                case INVOICE, PAID_COPY, PROFORMA -> "Invoice No.:";
                case CREDIT_NOTE -> "Credit note No.:";
                case QUOTE -> "Quote No.:";
                case ORDER_CONFIRMATION -> "Order No.:";
            };
            var dateLabel = switch (kind) {
                case INVOICE, PAID_COPY, PROFORMA -> "Invoice Date:";
                case CREDIT_NOTE -> "Date:";
                case QUOTE -> "Quote Date:";
                case ORDER_CONFIRMATION -> "Order Date:";
            };
            var due = kind == Kind.QUOTE ? "Valid until:" : "Due Date:";
            return new Copy("en", title, NorwegianTheme.ORG_NUMBER_EN, numberLabel, dateLabel,
                "Our ref.:", "Your ref.:", "Payment Information", due,
                "Bank Name:", "Account Number", "IBAN", "Sum", "VAT specification",
                "Payable", "Amount", "Invoice Amount", "Paid Amount", "Outstanding Amount",
                "Payment Receipt", "Last Payment Date", "Paid with",
                "Card / payment service", "Please quote invoice number ", " when paying",
                "Pay with QR", "This is not a VAT invoice.",
                "Description", "Comment", "Quantity", "Unit Price\n(excl. VAT)", "Discount", "VAT",
                "Amount\n(excl. VAT)", "Amount\n(incl. VAT)");
        }

        List<String> lineHeaders(boolean discount) {
            var headers = new ArrayList<String>();
            headers.add(description);
            headers.add(comment);
            headers.add(quantity);
            headers.add(unitPrice);
            if (discount) {
                headers.add(this.discount);
            }
            headers.add(amountEx);
            headers.add(vatHeader);
            headers.add(amountInc);
            return List.copyOf(headers);
        }

        String payableTitle(Model model) {
            if (model.kind() == Kind.CREDIT_NOTE) {
                return amount;
            }
            if (model.payment() != null) {
                return invoiceAmount;
            }
            if (model.kind() == Kind.QUOTE) {
                return locale.startsWith("nb") ? "Tilbudssum" : "Quote total";
            }
            if (model.kind() == Kind.ORDER_CONFIRMATION) {
                return sum;
            }
            return payable;
        }

        String creditFor(CreditFor credit) {
            if (locale.startsWith("nb")) {
                return "Kreditnota for faktura " + credit.invoiceNumber()
                    + " datert " + NorwegianMoney.date(credit.invoiceDate());
            }
            return "Credit note for invoice " + credit.invoiceNumber()
                + " dated " + NorwegianMoney.date(credit.invoiceDate());
        }
    }
}
