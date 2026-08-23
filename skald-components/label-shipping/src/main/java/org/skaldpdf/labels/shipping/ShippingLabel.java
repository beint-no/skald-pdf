package org.skaldpdf.labels.shipping;

import org.skaldpdf.Pdf;
import org.skaldpdf.barcode.Code128Barcode;
import org.skaldpdf.barcode.QrCode;
import org.skaldpdf.geom.PageSize;
import org.skaldpdf.layout.Document;
import org.skaldpdf.layout.borders.Border;
import org.skaldpdf.layout.borders.SolidBorder;
import org.skaldpdf.layout.element.Cell;
import org.skaldpdf.layout.element.Image;
import org.skaldpdf.layout.element.Paragraph;
import org.skaldpdf.layout.element.Table;
import org.skaldpdf.layout.properties.HorizontalAlignment;
import org.skaldpdf.layout.properties.UnitValue;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * 100 mm × 150 mm shipping label: from/to addresses, service, and tracking
 * as Code 128 plus an optional QR.
 *
 * <p>This is print stock, not a barcode primitive. Encoding lives in
 * {@code skald-barcode}; this class composes a finished label.
 */
public final class ShippingLabel {
    public static final float PAGE_WIDTH_MM = 100f;
    public static final float PAGE_HEIGHT_MM = 150f;
    public static final PageSize PAGE_SIZE = new PageSize(millimetres(PAGE_WIDTH_MM), millimetres(PAGE_HEIGHT_MM));

    private static final float MM = 72f / 25.4f;

    private ShippingLabel() {
    }

    public record Address(String name, List<String> lines) {
        public Address {
            name = requireText(name, "name");
            lines = Objects.requireNonNullElse(lines, List.<String>of()).stream()
                .filter(Objects::nonNull)
                .map(String::strip)
                .toList();
        }

        public Address(String name, String... lines) {
            this(name, List.of(lines));
        }
    }

    public record Spec(
        Address from,
        Address to,
        String tracking,
        String service,
        String reference,
        String trackingUrl
    ) {
        public Spec {
            Objects.requireNonNull(from, "from");
            Objects.requireNonNull(to, "to");
            tracking = requireText(tracking, "tracking").replace(" ", "");
            service = service == null ? "" : service.strip();
            reference = reference == null ? "" : reference.strip();
            trackingUrl = trackingUrl == null || trackingUrl.isBlank() ? "" : trackingUrl.strip();
        }
    }

    public static byte[] pdf(Spec spec) {
        Objects.requireNonNull(spec, "spec");
        return Pdf.create(PAGE_SIZE, document -> draw(document, spec));
    }

    public static void write(Path path, Spec spec) {
        Objects.requireNonNull(spec, "spec");
        Pdf.write(path, PAGE_SIZE, org.skaldpdf.pdf.WriterProperties.defaults(),
            document -> draw(document, spec));
    }

    private static void draw(Document document, Spec spec) {
        document.setMargins(14, 14, 14, 14)
            .setTitle("Shipping " + spec.tracking())
            .setAuthor(spec.from.name());
        document.add(label("FROM"));
        addAddress(document, spec.from(), 10);
        document.add(label("TO").setMarginTop(10));
        addAddress(document, spec.to(), 13);
        var meta = new Table(UnitValue.createPercentArray(new float[] {1, 1}))
            .useAllAvailableWidth()
            .setBorder(Border.NO_BORDER)
            .setMarginTop(12);
        meta.addCell(metaCell("SERVICE", spec.service()));
        meta.addCell(metaCell("REF", spec.reference()));
        document.add(meta);
        var barcode = new Code128Barcode(spec.tracking())
            .withBarHeight(36f)
            .withModuleWidth(1.1f)
            .withFontSize(8f);
        document.add(new Image(barcode).scaleToFit(PAGE_SIZE.getWidth() - 28, 56)
            .setHorizontalAlignment(HorizontalAlignment.CENTER)
            .setMarginTop(14));
        if (!spec.trackingUrl().isEmpty()) {
            document.add(new Image(new QrCode(spec.trackingUrl())).scaleInto(64, 64)
                .setHorizontalAlignment(HorizontalAlignment.CENTER)
                .setMarginTop(10));
        }
    }

    private static void addAddress(Document document, Address address, float nameSize) {
        document.add(new Paragraph(address.name()).bold().setFontSize(nameSize).setMarginBottom(1));
        for (var line : address.lines()) {
            if (!line.isBlank()) {
                document.add(new Paragraph(line).setFontSize(9).setMarginBottom(0.4f).setMultipliedLeading(1f));
            }
        }
    }

    private static Paragraph label(String text) {
        return new Paragraph(text)
            .setFontSize(7)
            .setFontColor(new org.skaldpdf.colors.DeviceRgb(110, 110, 110));
    }

    private static Cell metaCell(String label, String value) {
        var cell = new Cell().setBorder(Border.NO_BORDER).setPadding(2)
            .setBorderTop(new SolidBorder(0.4f));
        cell.add(new Paragraph(label).setFontSize(7)
            .setFontColor(new org.skaldpdf.colors.DeviceRgb(110, 110, 110)));
        if (!value.isBlank()) {
            cell.add(new Paragraph(value).bold().setFontSize(10).setMarginTop(1));
        }
        return cell;
    }

    private static float millimetres(float value) {
        return value * MM;
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field);
        var stripped = value.strip();
        if (stripped.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return stripped;
    }
}
