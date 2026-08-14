package org.skaldpdf.purchase.no;

import org.skaldpdf.invoice.no.Company;
import org.skaldpdf.invoice.no.LineItem;
import org.skaldpdf.invoice.no.NorwegianInvoice;
import org.skaldpdf.invoice.no.NorwegianMoney;
import org.skaldpdf.invoice.no.NorwegianTheme;
import org.skaldpdf.invoice.no.Party;
import org.skaldpdf.image.ImageSource;
import org.skaldpdf.layout.Document;
import org.skaldpdf.layout.element.Paragraph;
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
 * Norwegian purchase order ({@code Innkjøpsordre}) from buyer to supplier.
 * Same letterhead and line math as the invoice theme.
 */
public final class NorwegianPurchaseOrder {
    private NorwegianPurchaseOrder() {
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
        NorwegianTheme.metadata(document, "Innkjøpsordre " + model.number(),
            model.company().name(), "nb-NO");
        NorwegianTheme.header(document, model.company(), NorwegianTheme.ORG_NUMBER_NB, model.logo());
        NorwegianTheme.party(document, model.supplier());
        NorwegianTheme.titleBlock(document, "Innkjøpsordre",
            "Ordrenr.:", model.number(),
            "Ordredato:", NorwegianMoney.date(model.orderDate()),
            "Ønsket levering:", model.neededBy() == null ? "" : NorwegianMoney.date(model.neededBy()),
            "Vår ref.:", model.reference());
        if (model.shipTo() != null) {
            document.add(new Paragraph("Leveres til").bold()
                .setFontSize(NorwegianTheme.FONT_NORMAL).setMarginTop(16).setMarginBottom(2));
            document.add(new Paragraph(model.shipTo().name())
                .setFontSize(NorwegianTheme.FONT_NORMAL).setMarginBottom(0.2f));
            for (var line : model.shipTo().addressLines()) {
                document.add(new Paragraph(line)
                    .setFontSize(NorwegianTheme.FONT_NORMAL).setMarginBottom(0.2f));
            }
        }
        var table = new Table(UnitValue.createPercentArray(new float[] {34, 16, 10, 14, 12, 14}))
            .useAllAvailableWidth()
            .setMarginTop(24);
        NorwegianTheme.headerCell(table, "Beskrivelse", TextAlignment.LEFT);
        NorwegianTheme.headerCell(table, "SKU", TextAlignment.LEFT);
        NorwegianTheme.headerCell(table, "Antall", TextAlignment.RIGHT);
        NorwegianTheme.headerCell(table, "Enhetspris", TextAlignment.RIGHT);
        NorwegianTheme.headerCell(table, "MVA", TextAlignment.RIGHT);
        NorwegianTheme.headerCell(table, "Beløp", TextAlignment.RIGHT);
        for (var line : model.lines()) {
            table.addCell(NorwegianTheme.cell(line.description(), false, NorwegianTheme.FONT_SMALL, TextAlignment.LEFT));
            table.addCell(NorwegianTheme.cell(line.comment(), false, NorwegianTheme.FONT_SMALL, TextAlignment.LEFT));
            table.addCell(NorwegianTheme.cell(NorwegianMoney.quantity(line.quantity()), false,
                NorwegianTheme.FONT_SMALL, TextAlignment.RIGHT));
            table.addCell(NorwegianTheme.cell(NorwegianMoney.format(line.unitPriceExVat()), false,
                NorwegianTheme.FONT_SMALL, TextAlignment.RIGHT));
            table.addCell(NorwegianTheme.cell(NorwegianMoney.percent(line.vatRate()), false,
                NorwegianTheme.FONT_SMALL, TextAlignment.RIGHT));
            table.addCell(NorwegianTheme.cell(NorwegianMoney.format(line.amountIncVat()), false,
                NorwegianTheme.FONT_SMALL, TextAlignment.RIGHT));
        }
        var totals = NorwegianInvoice.Totals.of(model.lines());
        table.addRule(1.25f);
        table.addCell(NorwegianTheme.cell("Sum eks. MVA", true, NorwegianTheme.FONT_SMALL, TextAlignment.LEFT, 4));
        table.addCell(NorwegianTheme.cell(NorwegianMoney.format(totals.exVat()), false,
            NorwegianTheme.FONT_SMALL, TextAlignment.RIGHT, 2));
        table.addCell(NorwegianTheme.cell("MVA", false, NorwegianTheme.FONT_SMALL, TextAlignment.LEFT, 4));
        table.addCell(NorwegianTheme.cell(NorwegianMoney.format(totals.vat()), false,
            NorwegianTheme.FONT_SMALL, TextAlignment.RIGHT, 2));
        table.addCell(NorwegianTheme.cell("Totalsum", true, NorwegianTheme.FONT_SMALL, TextAlignment.LEFT, 4));
        table.addCell(NorwegianTheme.cell(NorwegianMoney.format(model.currency(), totals.incVat()), true,
            NorwegianTheme.FONT_SMALL, TextAlignment.RIGHT, 2));
        document.add(table);
        if (model.notes() != null) {
            document.add(new Paragraph(model.notes())
                .setFontSize(NorwegianTheme.FONT_NORMAL).setMarginTop(16).setMultipliedLeading(1.15f));
        }
        NorwegianTheme.branding(document, model.footer());
    }

    public static final class Model {
        private final Company company;
        private final Party supplier;
        private final Party shipTo;
        private final String number;
        private final LocalDate orderDate;
        private final LocalDate neededBy;
        private final String reference;
        private final String currency;
        private final String notes;
        private final String footer;
        private final ImageSource logo;
        private final List<LineItem> lines;

        Model(Builder builder) {
            this.company = Objects.requireNonNull(builder.company, "company");
            this.supplier = Objects.requireNonNull(builder.supplier, "supplier");
            this.shipTo = builder.shipTo;
            this.number = Company.requireText(builder.number, "number");
            this.orderDate = Objects.requireNonNull(builder.orderDate, "orderDate");
            this.neededBy = builder.neededBy;
            this.reference = builder.reference == null || builder.reference.isBlank()
                ? null : builder.reference.strip();
            this.currency = builder.currency == null || builder.currency.isBlank()
                ? NorwegianMoney.NOK : builder.currency.strip();
            this.notes = builder.notes == null || builder.notes.isBlank() ? null : builder.notes.strip();
            this.footer = builder.footer;
            this.logo = builder.logo;
            this.lines = List.copyOf(builder.lines);
            if (this.lines.isEmpty()) {
                throw new IllegalArgumentException("A purchase order needs at least one line");
            }
        }

        public static Builder builder() {
            return new Builder();
        }

        public Company company() {
            return company;
        }

        public Party supplier() {
            return supplier;
        }

        public Party shipTo() {
            return shipTo;
        }

        public String number() {
            return number;
        }

        public LocalDate orderDate() {
            return orderDate;
        }

        public LocalDate neededBy() {
            return neededBy;
        }

        public String reference() {
            return reference;
        }

        public String currency() {
            return currency;
        }

        public String notes() {
            return notes;
        }

        public String footer() {
            return footer;
        }

        public ImageSource logo() {
            return logo;
        }

        public List<LineItem> lines() {
            return lines;
        }
    }

    public static final class Builder {
        private Company company;
        private Party supplier;
        private Party shipTo;
        private String number;
        private LocalDate orderDate;
        private LocalDate neededBy;
        private String reference;
        private String currency = NorwegianMoney.NOK;
        private String notes;
        private String footer;
        private ImageSource logo;
        private final List<LineItem> lines = new ArrayList<>();

        private Builder() {
        }

        public Builder company(Company company) {
            this.company = company;
            return this;
        }

        public Builder supplier(Party supplier) {
            this.supplier = supplier;
            return this;
        }

        public Builder shipTo(Party shipTo) {
            this.shipTo = shipTo;
            return this;
        }

        public Builder number(String number) {
            this.number = number;
            return this;
        }

        public Builder orderDate(LocalDate orderDate) {
            this.orderDate = orderDate;
            return this;
        }

        public Builder neededBy(LocalDate neededBy) {
            this.neededBy = neededBy;
            return this;
        }

        public Builder reference(String reference) {
            this.reference = reference;
            return this;
        }

        public Builder currency(String currency) {
            this.currency = currency;
            return this;
        }

        public Builder notes(String notes) {
            this.notes = notes;
            return this;
        }

        public Builder footer(String footer) {
            this.footer = footer;
            return this;
        }

        public Builder logo(ImageSource logo) {
            this.logo = logo;
            return this;
        }

        public Builder line(LineItem line) {
            lines.add(Objects.requireNonNull(line, "line"));
            return this;
        }

        public Builder line(String description, String sku, int quantity, String unitPrice, int vatRate) {
            return line(new LineItem(description, sku, BigDecimal.valueOf(quantity),
                NorwegianMoney.parse(unitPrice), BigDecimal.valueOf(vatRate)));
        }

        public Model build() {
            return new Model(this);
        }
    }
}
