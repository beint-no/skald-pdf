package org.skaldpdf.packing.no;

import org.jspecify.annotations.Nullable;
import org.skaldpdf.barcode.QrCode;
import org.skaldpdf.invoice.no.Company;
import org.skaldpdf.invoice.no.NorwegianMoney;
import org.skaldpdf.invoice.no.NorwegianTheme;
import org.skaldpdf.invoice.no.Party;
import org.skaldpdf.image.ImageSource;
import org.skaldpdf.layout.Document;
import org.skaldpdf.layout.element.Image;
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
 * Norwegian packing slip ({@code Pakkseddel}) or delivery note
 * ({@code Følgeseddel}). Same letterhead as {@code skald-invoice-no}.
 */
public final class NorwegianPackingSlip {
    private NorwegianPackingSlip() {
    }

    public enum Kind {
        PACKING_SLIP,
        DELIVERY_NOTE
    }

    public record Line(String description, String sku, BigDecimal quantity, String location) {
        public Line {
            description = Company.requireText(description, "description");
            sku = Company.optionalText(sku);
            quantity = Objects.requireNonNull(quantity, "quantity");
            if (quantity.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("quantity must be positive");
            }
            location = Company.optionalText(location);
        }

        public Line(String description, String sku, int quantity, String location) {
            this(description, sku, BigDecimal.valueOf(quantity), location);
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
        var packing = model.kind() == Kind.PACKING_SLIP;
        var title = packing ? "Pakkseddel" : "Følgeseddel";
        NorwegianTheme.metadata(document, title + " " + model.number(), model.company().name(), "nb-NO");
        NorwegianTheme.header(document, model.company(), NorwegianTheme.ORG_NUMBER_NB, model.logo());
        NorwegianTheme.party(document, model.recipient());
        NorwegianTheme.titleBlock(document, title,
            packing ? "Ordrenr.:" : "Følgeseddelnr.:", model.number(),
            "Leveringsdato:", NorwegianMoney.date(model.deliveryDate()),
            "Sporing:", model.tracking());
        document.add(new Paragraph(packing
            ? "Vennligst sjekk innholdet mot listen under."
            : "Følgende varer er sendt.")
            .setFontSize(NorwegianTheme.FONT_NORMAL).setMarginTop(16));
        var table = new Table(UnitValue.createPercentArray(new float[] {38, 22, 12, 14, 14}))
            .useAllAvailableWidth()
            .setMarginTop(16);
        NorwegianTheme.headerCell(table, "Vare", TextAlignment.LEFT);
        NorwegianTheme.headerCell(table, "SKU", TextAlignment.LEFT);
        NorwegianTheme.headerCell(table, "Antall", TextAlignment.RIGHT);
        NorwegianTheme.headerCell(table, packing ? "Lokasjon" : "Sendt", TextAlignment.LEFT);
        NorwegianTheme.headerCell(table, packing ? "Pakket" : "Mottatt", TextAlignment.LEFT);
        for (var line : model.lines()) {
            table.addCell(NorwegianTheme.cell(line.description(), false, NorwegianTheme.FONT_SMALL, TextAlignment.LEFT));
            table.addCell(NorwegianTheme.cell(line.sku(), false, NorwegianTheme.FONT_SMALL, TextAlignment.LEFT));
            table.addCell(NorwegianTheme.cell(NorwegianMoney.quantity(line.quantity()), false,
                NorwegianTheme.FONT_SMALL, TextAlignment.RIGHT));
            table.addCell(NorwegianTheme.cell(line.location(), false, NorwegianTheme.FONT_SMALL, TextAlignment.LEFT));
            table.addCell(NorwegianTheme.cell("☐", false, NorwegianTheme.FONT_SMALL, TextAlignment.LEFT));
        }
        document.add(table);
        if (!model.trackingUrl().isEmpty()) {
            document.add(new Image(new QrCode(model.trackingUrl())).scaleInto(72, 72).setMarginTop(18));
        }
        NorwegianTheme.branding(document, model.footer());
    }

    public static final class Model {
        private final Kind kind;
        private final Company company;
        private final Party recipient;
        private final String number;
        private final LocalDate deliveryDate;
        private final String tracking;
        private final String trackingUrl;
        private final String footer;
        private final @Nullable ImageSource logo;
        private final List<Line> lines;

        Model(Builder builder) {
            this.kind = builder.kind;
            this.company = Objects.requireNonNull(builder.company, "company");
            this.recipient = Objects.requireNonNull(builder.recipient, "recipient");
            this.number = Company.requireText(builder.number, "number");
            this.deliveryDate = Objects.requireNonNull(builder.deliveryDate, "deliveryDate");
            this.tracking = Company.optionalText(builder.tracking);
            this.trackingUrl = Company.optionalText(builder.trackingUrl);
            this.footer = Company.optionalText(builder.footer);
            this.logo = builder.logo;
            this.lines = List.copyOf(builder.lines);
            if (this.lines.isEmpty()) {
                throw new IllegalArgumentException("A packing slip needs at least one line");
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

        public Party recipient() {
            return recipient;
        }

        public String number() {
            return number;
        }

        public LocalDate deliveryDate() {
            return deliveryDate;
        }

        public String tracking() {
            return tracking;
        }

        public String trackingUrl() {
            return trackingUrl;
        }

        public String footer() {
            return footer;
        }

        public @Nullable ImageSource logo() {
            return logo;
        }

        public List<Line> lines() {
            return lines;
        }
    }

    @org.jspecify.annotations.NullUnmarked
    public static final class Builder {
        private Kind kind = Kind.PACKING_SLIP;
        private Company company;
        private Party recipient;
        private String number;
        private LocalDate deliveryDate;
        private String tracking;
        private String trackingUrl;
        private String footer;
        private ImageSource logo;
        private final List<Line> lines = new ArrayList<>();

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

        public Builder recipient(Party recipient) {
            this.recipient = recipient;
            return this;
        }

        public Builder number(String number) {
            this.number = number;
            return this;
        }

        public Builder deliveryDate(LocalDate deliveryDate) {
            this.deliveryDate = deliveryDate;
            return this;
        }

        public Builder tracking(String tracking) {
            this.tracking = tracking;
            return this;
        }

        public Builder trackingUrl(String trackingUrl) {
            this.trackingUrl = trackingUrl;
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

        public Builder line(Line line) {
            lines.add(Objects.requireNonNull(line, "line"));
            return this;
        }

        public Builder line(String description, String sku, int quantity, String location) {
            return line(new Line(description, sku, quantity, location));
        }

        public Model build() {
            return new Model(this);
        }
    }
}
