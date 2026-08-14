package org.skaldpdf.reminder.no;

import org.skaldpdf.invoice.no.Company;
import org.skaldpdf.invoice.no.NorwegianMoney;
import org.skaldpdf.invoice.no.NorwegianTheme;
import org.skaldpdf.invoice.no.Party;
import org.skaldpdf.layout.Document;
import org.skaldpdf.layout.borders.SolidBorder;
import org.skaldpdf.layout.element.Paragraph;
import org.skaldpdf.layout.element.Table;
import org.skaldpdf.layout.properties.TextAlignment;
import org.skaldpdf.layout.properties.UnitValue;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Norwegian payment reminder ({@code Purring}) or collection notice
 * ({@code Betalingsoppfordring}). Legal default text follows common
 * Norwegian dunning practice; override {@link Model.Builder#body(String)}
 * if you need counsel-approved wording.
 */
public final class NorwegianReminder {
    public static final String REMINDER_BODY = "Vi ser ikke ut til å ha mottatt betaling for "
        + "fakturaen under. Dersom betaling skjer etter forfallsdato kan rente- og purregebyr "
        + "bli lagt til. Dersom betalingen er gjort de siste dagene, vennligst se bort fra "
        + "denne påminnelsen.";
    public static final String COLLECTION_BODY = "Vi ser ikke ut til å ha mottatt betaling for "
        + "fakturaen nedenfor. De varsles med dette om at skyldig beløp må være betalt innen "
        + "14 dager fra datoen for dette varselet. Dersom kravet ikke betales i sin helhet "
        + "innen fristen, kan kravet bli begjært tvangsinnfordret (utlegg) gjennom "
        + "namsmyndighetene, jf. tvangsfullbyrdelsesloven §4-18 og §4-19.";

    private NorwegianReminder() {
    }

    public enum Kind {
        REMINDER,
        COLLECTION
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
        var title = model.kind() == Kind.COLLECTION ? "Betalingsoppfordring" : "Purring";
        NorwegianTheme.metadata(document, title + " for faktura " + model.invoiceNumber(),
            model.company().name(), "nb-NO");
        NorwegianTheme.header(document, model.company(), NorwegianTheme.ORG_NUMBER_NB, model.logo());
        NorwegianTheme.party(document, model.customer());
        document.add(new Paragraph(title).bold().setFontSize(18).setMarginTop(12));
        document.add(new Paragraph(title + " for faktura " + model.invoiceNumber())
            .setFontSize(NorwegianTheme.FONT_NORMAL).setMultipliedLeading(1f).setMarginBottom(8));
        document.add(new Paragraph(model.body())
            .setFontSize(NorwegianTheme.FONT_NORMAL).setMultipliedLeading(1.15f));
        var table = new Table(UnitValue.createPercentArray(new float[] {18, 18, 18, 18, 28}))
            .useAllAvailableWidth()
            .setMarginTop(16);
        NorwegianTheme.headerCell(table, "Fakturanr.", TextAlignment.LEFT);
        NorwegianTheme.headerCell(table, "Fakturadato", TextAlignment.LEFT);
        NorwegianTheme.headerCell(table, "Forfallsdato", TextAlignment.LEFT);
        NorwegianTheme.headerCell(table, "Beløp", TextAlignment.RIGHT);
        NorwegianTheme.headerCell(table, "Beskrivelse / Spesifikasjon", TextAlignment.LEFT);
        addRow(table, model.invoiceNumber(), NorwegianMoney.date(model.invoiceDate()),
            NorwegianMoney.date(model.dueDate()),
            NorwegianMoney.format(model.currency(), model.originalAmount()),
            "Opprinnelig faktura");
        if (model.lateFee().compareTo(BigDecimal.ZERO) != 0) {
            addRow(table, "", NorwegianMoney.date(model.noticeDate()), "",
                NorwegianMoney.format(model.lateFee()), "Purregebyr");
        }
        if (model.interest().compareTo(BigDecimal.ZERO) != 0) {
            addRow(table, "", NorwegianMoney.date(model.noticeDate()), "",
                NorwegianMoney.format(model.interest()),
                "Renter " + NorwegianMoney.percent(model.interestRate())
                    + " · " + model.interestDays() + " rentedager");
        }
        table.addCell(NorwegianTheme.empty(3).setBorderTop(new SolidBorder(2f)));
        table.addCell(NorwegianTheme.cell("Til betaling", true, NorwegianTheme.FONT_SMALL, TextAlignment.LEFT));
        table.addCell(NorwegianTheme.cell(NorwegianMoney.format(model.currency(), model.payable()),
            true, NorwegianTheme.FONT_SMALL, TextAlignment.RIGHT));
        document.add(table);
        NorwegianTheme.branding(document, model.footer());
    }

    private static void addRow(Table table, String number, String issued, String due,
                               String amount, String description) {
        table.addCell(NorwegianTheme.cell(number, false, NorwegianTheme.FONT_SMALL, TextAlignment.LEFT));
        table.addCell(NorwegianTheme.cell(issued, false, NorwegianTheme.FONT_SMALL, TextAlignment.LEFT));
        table.addCell(NorwegianTheme.cell(due, false, NorwegianTheme.FONT_SMALL, TextAlignment.LEFT));
        table.addCell(NorwegianTheme.cell(amount, false, NorwegianTheme.FONT_SMALL, TextAlignment.RIGHT));
        table.addCell(NorwegianTheme.cell(description, false, NorwegianTheme.FONT_SMALL, TextAlignment.LEFT));
    }

    public static final class Model {
        private final Kind kind;
        private final Company company;
        private final Party customer;
        private final String invoiceNumber;
        private final LocalDate invoiceDate;
        private final LocalDate dueDate;
        private final LocalDate noticeDate;
        private final String currency;
        private final BigDecimal originalAmount;
        private final BigDecimal lateFee;
        private final BigDecimal interest;
        private final BigDecimal interestRate;
        private final int interestDays;
        private final String body;
        private final String footer;
        private final byte[] logo;

        Model(Builder builder) {
            this.kind = builder.kind;
            this.company = Objects.requireNonNull(builder.company, "company");
            this.customer = Objects.requireNonNull(builder.customer, "customer");
            this.invoiceNumber = Company.requireText(builder.invoiceNumber, "invoiceNumber");
            this.invoiceDate = Objects.requireNonNull(builder.invoiceDate, "invoiceDate");
            this.dueDate = Objects.requireNonNull(builder.dueDate, "dueDate");
            this.noticeDate = Objects.requireNonNull(builder.noticeDate, "noticeDate");
            this.currency = builder.currency == null || builder.currency.isBlank()
                ? NorwegianMoney.NOK : builder.currency.strip();
            this.originalAmount = NorwegianMoney.amount(builder.originalAmount);
            this.lateFee = NorwegianMoney.amount(builder.lateFee);
            this.interest = NorwegianMoney.amount(builder.interest);
            this.interestRate = builder.interestRate;
            this.interestDays = builder.interestDays;
            this.body = builder.body == null || builder.body.isBlank()
                ? (kind == Kind.COLLECTION ? COLLECTION_BODY : REMINDER_BODY)
                : builder.body.strip();
            this.footer = builder.footer;
            this.logo = builder.logo;
            if (interestDays < 0) {
                throw new IllegalArgumentException("interestDays must not be negative");
            }
        }

        public static Builder builder() {
            return new Builder();
        }

        public Kind kind() {
            return kind;
        }

        public Company company() {
            return company;
        }

        public Party customer() {
            return customer;
        }

        public String invoiceNumber() {
            return invoiceNumber;
        }

        public LocalDate invoiceDate() {
            return invoiceDate;
        }

        public LocalDate dueDate() {
            return dueDate;
        }

        public LocalDate noticeDate() {
            return noticeDate;
        }

        public String currency() {
            return currency;
        }

        public BigDecimal originalAmount() {
            return originalAmount;
        }

        public BigDecimal lateFee() {
            return lateFee;
        }

        public BigDecimal interest() {
            return interest;
        }

        public BigDecimal interestRate() {
            return interestRate;
        }

        public int interestDays() {
            return interestDays;
        }

        public BigDecimal payable() {
            return originalAmount.add(lateFee).add(interest);
        }

        public String body() {
            return body;
        }

        public String footer() {
            return footer;
        }

        public byte[] logo() {
            return logo;
        }
    }

    public static final class Builder {
        private Kind kind = Kind.REMINDER;
        private Company company;
        private Party customer;
        private String invoiceNumber;
        private LocalDate invoiceDate;
        private LocalDate dueDate;
        private LocalDate noticeDate;
        private String currency = NorwegianMoney.NOK;
        private BigDecimal originalAmount = BigDecimal.ZERO;
        private BigDecimal lateFee = BigDecimal.ZERO;
        private BigDecimal interest = BigDecimal.ZERO;
        private BigDecimal interestRate = new BigDecimal("12");
        private int interestDays;
        private String body;
        private String footer;
        private byte[] logo;

        private Builder() {
        }

        public Builder kind(Kind kind) {
            this.kind = Objects.requireNonNull(kind, "kind");
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

        public Builder invoiceNumber(String invoiceNumber) {
            this.invoiceNumber = invoiceNumber;
            return this;
        }

        public Builder invoiceDate(LocalDate invoiceDate) {
            this.invoiceDate = invoiceDate;
            return this;
        }

        public Builder dueDate(LocalDate dueDate) {
            this.dueDate = dueDate;
            return this;
        }

        public Builder noticeDate(LocalDate noticeDate) {
            this.noticeDate = noticeDate;
            return this;
        }

        public Builder currency(String currency) {
            this.currency = currency;
            return this;
        }

        public Builder originalAmount(BigDecimal originalAmount) {
            this.originalAmount = originalAmount;
            return this;
        }

        public Builder originalAmount(String originalAmount) {
            return originalAmount(NorwegianMoney.parse(originalAmount));
        }

        public Builder lateFee(BigDecimal lateFee) {
            this.lateFee = lateFee;
            return this;
        }

        public Builder lateFee(String lateFee) {
            return lateFee(NorwegianMoney.parse(lateFee));
        }

        public Builder interest(BigDecimal interest, BigDecimal rate, int days) {
            this.interest = interest;
            this.interestRate = Objects.requireNonNull(rate, "rate");
            this.interestDays = days;
            return this;
        }

        public Builder interest(String interest, String rate, int days) {
            return interest(NorwegianMoney.parse(interest), NorwegianMoney.parse(rate), days);
        }

        public Builder body(String body) {
            this.body = body;
            return this;
        }

        public Builder footer(String footer) {
            this.footer = footer;
            return this;
        }

        public Builder logo(byte[] logo) {
            this.logo = logo;
            return this;
        }

        public Model build() {
            return new Model(this);
        }
    }
}
