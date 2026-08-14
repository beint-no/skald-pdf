package org.skaldpdf.invoice.no;

import org.skaldpdf.Pdf;
import org.skaldpdf.colors.DeviceRgb;
import org.skaldpdf.font.PdfFont;
import org.skaldpdf.font.PdfFontFactory;
import org.skaldpdf.geom.PageSize;
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
import org.skaldpdf.pdf.WriterProperties;

import java.nio.file.Path;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Shared ReAI-style Norwegian letterhead: 40 pt A4 margins, 14 pt company
 * name on the right, 2 pt rule, organisation number, and a grey footer line.
 *
 * <p>Sibling components (packing slip, reminder, statement, purchase order)
 * use this so a set of documents from one issuer looks like one family.
 */
public final class NorwegianTheme {
    public static final float MARGIN = 40f;
    public static final float FONT_NORMAL = 10f;
    public static final float FONT_SMALL = 9f;
    public static final float FONT_HEADER = 14f;
    public static final float FONT_TITLE = 18f;
    public static final DeviceRgb BRAND_GRAY = new DeviceRgb(150, 150, 150);

    public static final String ORG_NUMBER_NB = "Organisasjonsnummer:";
    public static final String ORG_NUMBER_EN = "Company Number:";

    private NorwegianTheme() {
    }

    public static byte[] create(Consumer<Document> content) {
        return Pdf.create(PageSize.A4, WriterProperties.defaults(), document -> {
            prepare(document);
            content.accept(document);
        });
    }

    public static byte[] create(PageSize pageSize, Consumer<Document> content) {
        return Pdf.create(pageSize, WriterProperties.defaults(), document -> {
            prepare(document);
            content.accept(document);
        });
    }

    public static void write(Path path, Consumer<Document> content) {
        Pdf.write(path, PageSize.A4, WriterProperties.defaults(), document -> {
            prepare(document);
            content.accept(document);
        });
    }

    public static void prepare(Document document) {
        Objects.requireNonNull(document, "document");
        document.setMargins(MARGIN, MARGIN, MARGIN, MARGIN)
            .setFooter(14, page -> new Paragraph(page.pageNumber() + " / " + page.pageCount())
                .setFontSize(8)
                .setFontColor(BRAND_GRAY)
                .setTextAlignment(TextAlignment.CENTER));
    }

    public static void metadata(Document document, String title, String author, String language) {
        document.setTitle(title).setAuthor(author).setLanguage(language);
    }

    public static void header(Document document, Company company, String organizationLabel, byte[] logo) {
        Objects.requireNonNull(company, "company");
        var fonts = fonts();
        var label = organizationLabel == null || organizationLabel.isBlank()
            ? ORG_NUMBER_NB : organizationLabel;
        if (logo == null || logo.length == 0) {
            document.add(new Paragraph(company.name())
                .setFont(fonts.bold)
                .setFontSize(FONT_HEADER)
                .setTextAlignment(TextAlignment.RIGHT)
                .setMarginBottom(2));
        } else {
            var row = new Table(UnitValue.createPercentArray(new float[] {50, 50}))
                .useAllAvailableWidth()
                .setBorder(Border.NO_BORDER)
                .setMarginBottom(2);
            row.addCell(new Cell().setBorder(Border.NO_BORDER).setPadding(0)
                .add(new Image(ImageDataFactory.create(logo)).scaleToFit(160, 60)));
            row.addCell(new Cell().setBorder(Border.NO_BORDER).setPadding(0)
                .add(new Paragraph(company.name()).setFont(fonts.bold).setFontSize(FONT_HEADER)
                    .setTextAlignment(TextAlignment.RIGHT)));
            document.add(row);
        }
        document.add(new LineSeparator(new SolidLine(2f)).setMarginTop(2).setMarginBottom(4));
        if (!company.addressLine().isBlank()) {
            document.add(new Paragraph(company.addressLine())
                .setFont(fonts.regular)
                .setFontSize(FONT_NORMAL)
                .setWidth(UnitValue.createPercentValue(40f))
                .setTextAlignment(TextAlignment.LEFT)
                .setHorizontalAlignment(HorizontalAlignment.RIGHT)
                .setMarginBottom(0)
                .setMultipliedLeading(1f));
        }
        var orgValue = company.formattedOrganizationNumber();
        var labelWidth = fonts.bold.getWidth(label, FONT_NORMAL) + 8;
        var valueWidth = fonts.regular.getWidth(orgValue, FONT_NORMAL) + 8;
        var org = Table.withColumns(UnitValue.createPointValue(labelWidth), UnitValue.createPointValue(valueWidth))
            .setWidth(UnitValue.createPointValue(labelWidth + valueWidth))
            .setHorizontalAlignment(HorizontalAlignment.RIGHT)
            .setBorder(Border.NO_BORDER)
            .setMarginTop(10);
        org.addCell(cell(label, true, FONT_NORMAL, TextAlignment.LEFT));
        org.addCell(cell(orgValue, false, FONT_NORMAL, TextAlignment.LEFT));
        document.add(org);
    }

    public static void party(Document document, Party party) {
        Objects.requireNonNull(party, "party");
        var fonts = fonts();
        document.add(new Paragraph(party.name())
            .setFont(fonts.regular).setFontSize(FONT_NORMAL)
            .setMarginTop(18).setMarginBottom(0.2f).setMultipliedLeading(1f));
        for (var line : party.addressLines()) {
            if (line == null || line.isBlank()) {
                continue;
            }
            document.add(new Paragraph(line)
                .setFont(fonts.regular).setFontSize(FONT_NORMAL)
                .setMarginBottom(0.2f).setMultipliedLeading(1f));
        }
    }

    public static void titleBlock(Document document, String title, String... rows) {
        if (rows.length % 2 != 0) {
            throw new IllegalArgumentException("titleBlock rows must be label/value pairs");
        }
        var fonts = fonts();
        var table = new Table(new float[] {1, 1})
            .setWidth(UnitValue.createPercentValue(50f))
            .setHorizontalAlignment(HorizontalAlignment.RIGHT)
            .setBorder(Border.NO_BORDER);
        table.addCell(new Cell(2).setBorder(Border.NO_BORDER).setPaddingBottom(2)
            .add(new Paragraph(title).setFont(fonts.bold).setFontSize(FONT_TITLE)));
        for (int index = 0; index < rows.length; index += 2) {
            if (rows[index + 1] == null || rows[index + 1].isBlank()) {
                continue;
            }
            table.addCell(cell(rows[index], false, FONT_NORMAL, TextAlignment.LEFT));
            table.addCell(cell(rows[index + 1], false, FONT_NORMAL, TextAlignment.RIGHT));
        }
        table.addCell(empty(2).setPaddingTop(4));
        document.add(table);
    }

    public static void labeledBlock(Document document, String heading, String... rows) {
        if (rows.length % 2 != 0) {
            throw new IllegalArgumentException("labeledBlock rows must be label/value pairs");
        }
        var fonts = fonts();
        var table = new Table(new float[] {2, 5})
            .setWidth(UnitValue.createPercentValue(50f))
            .setHorizontalAlignment(HorizontalAlignment.RIGHT)
            .setBorder(Border.NO_BORDER);
        table.addCell(new Cell(2).setBorder(Border.NO_BORDER).setPaddingBottom(2)
            .add(new Paragraph(heading).setFont(fonts.bold)));
        for (int index = 0; index < rows.length; index += 2) {
            if (rows[index + 1] == null || rows[index + 1].isBlank()) {
                continue;
            }
            table.addCell(cell(rows[index], false, FONT_NORMAL, TextAlignment.LEFT));
            table.addCell(cell(rows[index + 1], false, FONT_NORMAL, TextAlignment.RIGHT));
        }
        document.add(table);
    }

    public static void branding(Document document, String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        document.add(new Paragraph(text)
            .setFontSize(FONT_SMALL)
            .setFontColor(BRAND_GRAY)
            .setTextAlignment(TextAlignment.CENTER)
            .setMarginTop(30));
    }

    public static void headerCell(Table table, String text, TextAlignment alignment) {
        table.addHeaderCell(cell(text, true, FONT_SMALL, alignment)
            .setBorder(Border.NO_BORDER)
            .setBorderBottom(new SolidBorder(0.25f))
            .setPadding(3));
    }

    public static Cell cell(String text, boolean bold, float size, TextAlignment alignment) {
        return cell(text, bold, size, alignment, 1);
    }

    public static Cell cell(String text, boolean bold, float size, TextAlignment alignment, int colspan) {
        var paragraph = new Paragraph(text == null ? "" : text)
            .setFontSize(size)
            .setMultipliedLeading(1f);
        if (bold) {
            paragraph.bold();
        }
        return new Cell(colspan).add(paragraph)
            .setTextAlignment(alignment)
            .setBorder(Border.NO_BORDER)
            .setPadding(2);
    }

    public static Cell empty(int colspan) {
        return new Cell(colspan).setBorder(Border.NO_BORDER);
    }

    public static Fonts fonts() {
        return new Fonts(PdfFontFactory.regular(), PdfFontFactory.bold());
    }

    public record Fonts(PdfFont regular, PdfFont bold) {
    }
}
