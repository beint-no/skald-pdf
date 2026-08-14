package org.skaldpdf.statement.no;

import org.skaldpdf.invoice.no.Company;
import org.skaldpdf.invoice.no.NorwegianMoney;
import org.skaldpdf.invoice.no.NorwegianTheme;
import org.skaldpdf.invoice.no.Party;
import org.skaldpdf.layout.Document;
import org.skaldpdf.layout.element.Table;
import org.skaldpdf.layout.properties.TextAlignment;
import org.skaldpdf.layout.properties.UnitValue;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Norwegian statement of account ({@code Kontooversikt}): opening balance,
 * dated entries, running balance, closing balance.
 */
public final class NorwegianStatement {
    private NorwegianStatement() {
    }

    public record Entry(LocalDate date, String reference, String description,
                        BigDecimal debit, BigDecimal credit) {
        public Entry {
            Objects.requireNonNull(date, "date");
            reference = reference == null ? "" : reference.strip();
            description = Company.requireText(description, "description");
            debit = debit == null ? BigDecimal.ZERO.setScale(2) : NorwegianMoney.amount(debit);
            credit = credit == null ? BigDecimal.ZERO.setScale(2) : NorwegianMoney.amount(credit);
            if (debit.compareTo(BigDecimal.ZERO) < 0 || credit.compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException("debit and credit must not be negative");
            }
            if (debit.compareTo(BigDecimal.ZERO) > 0 && credit.compareTo(BigDecimal.ZERO) > 0) {
                throw new IllegalArgumentException("An entry cannot be both debit and credit");
            }
        }

        public BigDecimal signedAmount() {
            return debit.subtract(credit);
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
        NorwegianTheme.metadata(document, "Kontooversikt " + model.number(),
            model.company().name(), "nb-NO");
        NorwegianTheme.header(document, model.company(), NorwegianTheme.ORG_NUMBER_NB, model.logo());
        NorwegianTheme.party(document, model.customer());
        NorwegianTheme.titleBlock(document, "Kontooversikt",
            "Utskriftnr.:", model.number(),
            "Periode:", NorwegianMoney.date(model.periodStart()) + " – "
                + NorwegianMoney.date(model.periodEnd()));
        var table = new Table(UnitValue.createPercentArray(new float[] {14, 14, 30, 14, 14, 14}))
            .useAllAvailableWidth()
            .setMarginTop(24);
        NorwegianTheme.headerCell(table, "Dato", TextAlignment.LEFT);
        NorwegianTheme.headerCell(table, "Ref.", TextAlignment.LEFT);
        NorwegianTheme.headerCell(table, "Beskrivelse", TextAlignment.LEFT);
        NorwegianTheme.headerCell(table, "Debet", TextAlignment.RIGHT);
        NorwegianTheme.headerCell(table, "Kredit", TextAlignment.RIGHT);
        NorwegianTheme.headerCell(table, "Saldo", TextAlignment.RIGHT);
        addAmountRow(table, "", "", "Inngående saldo", "", "", model.openingBalance(), true);
        var running = model.openingBalance();
        for (var entry : model.entries()) {
            running = NorwegianMoney.amount(running.add(entry.signedAmount()));
            addAmountRow(table, NorwegianMoney.date(entry.date()), entry.reference(), entry.description(),
                zeroBlank(entry.debit()), zeroBlank(entry.credit()), running, false);
        }
        table.addRule(1.25f);
        addAmountRow(table, "", "", "Utgående saldo", "", "", running, true);
        document.add(table);
        NorwegianTheme.branding(document, model.footer());
    }

    private static void addAmountRow(Table table, String date, String reference, String description,
                                     String debit, String credit, BigDecimal balance, boolean bold) {
        table.addCell(NorwegianTheme.cell(date, bold, NorwegianTheme.FONT_SMALL, TextAlignment.LEFT));
        table.addCell(NorwegianTheme.cell(reference, bold, NorwegianTheme.FONT_SMALL, TextAlignment.LEFT));
        table.addCell(NorwegianTheme.cell(description, bold, NorwegianTheme.FONT_SMALL, TextAlignment.LEFT));
        table.addCell(NorwegianTheme.cell(debit, bold, NorwegianTheme.FONT_SMALL, TextAlignment.RIGHT));
        table.addCell(NorwegianTheme.cell(credit, bold, NorwegianTheme.FONT_SMALL, TextAlignment.RIGHT));
        table.addCell(NorwegianTheme.cell(NorwegianMoney.format(balance), bold,
            NorwegianTheme.FONT_SMALL, TextAlignment.RIGHT));
    }

    private static String zeroBlank(BigDecimal amount) {
        return amount.compareTo(BigDecimal.ZERO) == 0 ? "" : NorwegianMoney.format(amount);
    }

    public static final class Model {
        private final Company company;
        private final Party customer;
        private final String number;
        private final LocalDate periodStart;
        private final LocalDate periodEnd;
        private final BigDecimal openingBalance;
        private final String footer;
        private final byte[] logo;
        private final List<Entry> entries;

        Model(Builder builder) {
            this.company = Objects.requireNonNull(builder.company, "company");
            this.customer = Objects.requireNonNull(builder.customer, "customer");
            this.number = Company.requireText(builder.number, "number");
            this.periodStart = Objects.requireNonNull(builder.periodStart, "periodStart");
            this.periodEnd = Objects.requireNonNull(builder.periodEnd, "periodEnd");
            if (periodEnd.isBefore(periodStart)) {
                throw new IllegalArgumentException("periodEnd must not be before periodStart");
            }
            this.openingBalance = NorwegianMoney.amount(builder.openingBalance);
            this.footer = builder.footer;
            this.logo = builder.logo;
            this.entries = List.copyOf(builder.entries);
        }

        public static Builder builder() {
            return new Builder();
        }

        public Company company() {
            return company;
        }

        public Party customer() {
            return customer;
        }

        public String number() {
            return number;
        }

        public LocalDate periodStart() {
            return periodStart;
        }

        public LocalDate periodEnd() {
            return periodEnd;
        }

        public BigDecimal openingBalance() {
            return openingBalance;
        }

        public BigDecimal closingBalance() {
            var running = openingBalance;
            for (var entry : entries) {
                running = running.add(entry.signedAmount());
            }
            return NorwegianMoney.amount(running);
        }

        public String footer() {
            return footer;
        }

        public byte[] logo() {
            return logo;
        }

        public List<Entry> entries() {
            return entries;
        }
    }

    public static final class Builder {
        private Company company;
        private Party customer;
        private String number;
        private LocalDate periodStart;
        private LocalDate periodEnd;
        private BigDecimal openingBalance = BigDecimal.ZERO;
        private String footer;
        private byte[] logo;
        private final List<Entry> entries = new ArrayList<>();

        private Builder() {
        }

        public Builder company(Company company) {
            this.company = company;
            return this;
        }

        public Builder customer(Party customer) {
            this.customer = customer;
            return this;
        }

        public Builder number(String number) {
            this.number = number;
            return this;
        }

        public Builder period(LocalDate start, LocalDate end) {
            this.periodStart = start;
            this.periodEnd = end;
            return this;
        }

        public Builder openingBalance(BigDecimal openingBalance) {
            this.openingBalance = openingBalance;
            return this;
        }

        public Builder openingBalance(String openingBalance) {
            return openingBalance(NorwegianMoney.parse(openingBalance));
        }

        public Builder footer(String footer) {
            this.footer = footer;
            return this;
        }

        public Builder logo(byte[] logo) {
            this.logo = logo;
            return this;
        }

        public Builder debit(LocalDate date, String reference, String description, BigDecimal amount) {
            entries.add(new Entry(date, reference, description, amount, BigDecimal.ZERO));
            return this;
        }

        public Builder debit(LocalDate date, String reference, String description, String amount) {
            return debit(date, reference, description, NorwegianMoney.parse(amount));
        }

        public Builder credit(LocalDate date, String reference, String description, BigDecimal amount) {
            entries.add(new Entry(date, reference, description, BigDecimal.ZERO, amount));
            return this;
        }

        public Builder credit(LocalDate date, String reference, String description, String amount) {
            return credit(date, reference, description, NorwegianMoney.parse(amount));
        }

        public Model build() {
            return new Model(this);
        }
    }
}
