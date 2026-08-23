package org.skaldpdf.receipt.no;

import org.jspecify.annotations.Nullable;
import org.skaldpdf.Pdf;
import org.skaldpdf.geom.PageSize;
import org.skaldpdf.invoice.no.Company;
import org.skaldpdf.invoice.no.LineItem;
import org.skaldpdf.invoice.no.NorwegianInvoice;
import org.skaldpdf.invoice.no.NorwegianMoney;
import org.skaldpdf.invoice.no.NorwegianTheme;
import org.skaldpdf.invoice.no.Party;
import org.skaldpdf.layout.Document;
import org.skaldpdf.layout.borders.Border;
import org.skaldpdf.layout.canvas.SolidLine;
import org.skaldpdf.layout.element.LineSeparator;
import org.skaldpdf.layout.element.Paragraph;
import org.skaldpdf.layout.element.Table;
import org.skaldpdf.layout.properties.TextAlignment;
import org.skaldpdf.layout.properties.UnitValue;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Compact Norwegian A5 sales receipt ({@code Kvittering}). Same money and
 * line math as {@link NorwegianInvoice}, smaller page.
 */
public final class NorwegianReceipt {
    public static final PageSize PAGE_SIZE = PageSize.A5;
    private static final DateTimeFormatter ISSUED = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    private NorwegianReceipt() {
    }

    public static byte[] pdf(Model model) {
        Objects.requireNonNull(model, "model");
        return Pdf.create(PAGE_SIZE, document -> {
            document.setMargins(28, 28, 28, 28);
            render(document, model);
        });
    }

    public static void write(Path path, Model model) {
        Objects.requireNonNull(model, "model");
        Pdf.write(path, PAGE_SIZE, org.skaldpdf.pdf.WriterProperties.defaults(), document -> {
            document.setMargins(28, 28, 28, 28);
            render(document, model);
        });
    }

    private static void render(Document document, Model model) {
        document.setTitle("Kvittering " + model.number())
            .setAuthor(model.company().name())
            .setLanguage("nb-NO");
        document.add(new Paragraph(model.company().name())
            .bold().setFontSize(14).setTextAlignment(TextAlignment.CENTER));
        if (!model.company().addressLine().isBlank()) {
            document.add(new Paragraph(model.company().addressLine())
                .setFontSize(9).setTextAlignment(TextAlignment.CENTER).setMarginTop(2));
        }
        document.add(new Paragraph(model.company().formattedOrganizationNumber())
            .setFontSize(9).setTextAlignment(TextAlignment.CENTER).setMarginTop(1));
        document.add(new LineSeparator(new SolidLine(1.25f)).setMarginTop(10).setMarginBottom(8));
        document.add(new Paragraph("Kvittering").bold().setFontSize(16)
            .setTextAlignment(TextAlignment.CENTER));
        document.add(new Paragraph(model.number() + "  ·  " + ISSUED.format(model.issuedAt()))
            .setFontSize(9).setTextAlignment(TextAlignment.CENTER)
            .setFontColor(NorwegianTheme.BRAND_GRAY).setMarginTop(2));
        if (model.customer() != null) {
            document.add(new Paragraph(model.customer().name())
                .setFontSize(NorwegianTheme.FONT_NORMAL).setMarginTop(12));
        }
        var table = new Table(UnitValue.createPercentArray(new float[] {46, 12, 20, 22}))
            .useAllAvailableWidth()
            .setBorder(Border.NO_BORDER)
            .setMarginTop(14);
        NorwegianTheme.headerCell(table, "Vare", TextAlignment.LEFT);
        NorwegianTheme.headerCell(table, "Ant", TextAlignment.RIGHT);
        NorwegianTheme.headerCell(table, "Pris", TextAlignment.RIGHT);
        NorwegianTheme.headerCell(table, "Sum", TextAlignment.RIGHT);
        var totals = NorwegianInvoice.Totals.of(model.lines());
        for (var line : model.lines()) {
            table.addCell(NorwegianTheme.cell(line.description(), false, NorwegianTheme.FONT_SMALL, TextAlignment.LEFT));
            table.addCell(NorwegianTheme.cell(NorwegianMoney.quantity(line.quantity()), false,
                NorwegianTheme.FONT_SMALL, TextAlignment.RIGHT));
            table.addCell(NorwegianTheme.cell(NorwegianMoney.format(line.unitPriceExVat()), false,
                NorwegianTheme.FONT_SMALL, TextAlignment.RIGHT));
            table.addCell(NorwegianTheme.cell(NorwegianMoney.format(line.amountIncVat()), false,
                NorwegianTheme.FONT_SMALL, TextAlignment.RIGHT));
        }
        table.addRule(1f);
        table.addCell(NorwegianTheme.cell("Eks. MVA", false, NorwegianTheme.FONT_SMALL,
            TextAlignment.LEFT, 3));
        table.addCell(NorwegianTheme.cell(NorwegianMoney.format(totals.exVat()), false,
            NorwegianTheme.FONT_SMALL, TextAlignment.RIGHT));
        table.addCell(NorwegianTheme.cell("MVA", false, NorwegianTheme.FONT_SMALL, TextAlignment.LEFT, 3));
        table.addCell(NorwegianTheme.cell(NorwegianMoney.format(totals.vat()), false,
            NorwegianTheme.FONT_SMALL, TextAlignment.RIGHT));
        table.addCell(NorwegianTheme.cell("Å betale", true, NorwegianTheme.FONT_NORMAL, TextAlignment.LEFT, 3));
        table.addCell(NorwegianTheme.cell(NorwegianMoney.format(model.currency(), totals.incVat()),
            true, NorwegianTheme.FONT_NORMAL, TextAlignment.RIGHT));
        document.add(table);
        if (!model.paymentMethod().isEmpty()) {
            document.add(new Paragraph("Betalt med " + model.paymentMethod())
                .setFontSize(NorwegianTheme.FONT_SMALL).setMarginTop(14));
        }
        NorwegianTheme.branding(document, model.footer());
    }

    public static final class Model {
        private final Company company;
        private final @Nullable Party customer;
        private final String number;
        private final LocalDateTime issuedAt;
        private final String currency;
        private final String paymentMethod;
        private final String footer;
        private final List<LineItem> lines;

        Model(Builder builder) {
            this.company = Objects.requireNonNull(builder.company, "company");
            this.customer = builder.customer;
            this.number = Company.requireText(builder.number, "number");
            this.issuedAt = Objects.requireNonNull(builder.issuedAt, "issuedAt");
            var currency = Company.optionalText(builder.currency);
            this.currency = currency.isEmpty() ? NorwegianMoney.NOK : currency;
            this.paymentMethod = Company.optionalText(builder.paymentMethod);
            this.footer = Company.optionalText(builder.footer);
            this.lines = List.copyOf(builder.lines);
            if (this.lines.isEmpty()) {
                throw new IllegalArgumentException("A receipt needs at least one line");
            }
        }

        public static Builder builder() {
            return new Builder();
        }

        public Company company() {
            return company;
        }

        public @Nullable Party customer() {
            return customer;
        }

        public String number() {
            return number;
        }

        public LocalDateTime issuedAt() {
            return issuedAt;
        }

        public String currency() {
            return currency;
        }

        public String paymentMethod() {
            return paymentMethod;
        }

        public String footer() {
            return footer;
        }

        public List<LineItem> lines() {
            return lines;
        }
    }

    @org.jspecify.annotations.NullUnmarked
    public static final class Builder {
        private Company company;
        private Party customer;
        private String number;
        private LocalDateTime issuedAt;
        private String currency = NorwegianMoney.NOK;
        private String paymentMethod;
        private String footer;
        private final List<LineItem> lines = new ArrayList<>();

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

        public Builder issuedAt(LocalDateTime issuedAt) {
            this.issuedAt = issuedAt;
            return this;
        }

        public Builder currency(String currency) {
            this.currency = currency;
            return this;
        }

        public Builder paymentMethod(String paymentMethod) {
            this.paymentMethod = paymentMethod;
            return this;
        }

        public Builder footer(String footer) {
            this.footer = footer;
            return this;
        }

        public Builder line(LineItem line) {
            lines.add(Objects.requireNonNull(line, "line"));
            return this;
        }

        public Builder line(String description, int quantity, String unitPrice, int vatRate) {
            return line(new LineItem(description, "", BigDecimal.valueOf(quantity),
                NorwegianMoney.parse(unitPrice), BigDecimal.valueOf(vatRate)));
        }

        public Model build() {
            return new Model(this);
        }
    }
}
