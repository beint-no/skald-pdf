package no.beint.skald.layout.internal;

import no.beint.skald.colors.Color;
import no.beint.skald.colors.ColorConstants;
import no.beint.skald.font.PdfFont;
import no.beint.skald.font.PdfFontFactory;
import no.beint.skald.geom.PageSize;
import no.beint.skald.geom.Rectangle;
import no.beint.skald.layout.Style;
import no.beint.skald.layout.borders.Border;
import no.beint.skald.layout.element.AbstractElement;
import no.beint.skald.layout.element.AreaBreak;
import no.beint.skald.layout.element.Cell;
import no.beint.skald.layout.element.Div;
import no.beint.skald.layout.element.Image;
import no.beint.skald.layout.element.LayoutElement;
import no.beint.skald.layout.element.LineSeparator;
import no.beint.skald.layout.element.Paragraph;
import no.beint.skald.layout.element.Table;
import no.beint.skald.layout.element.Text;
import no.beint.skald.layout.properties.HorizontalAlignment;
import no.beint.skald.layout.properties.TextAlignment;
import no.beint.skald.layout.properties.UnitValue;
import no.beint.skald.layout.properties.VerticalAlignment;
import no.beint.skald.pdf.PdfDocument;
import no.beint.skald.pdf.PdfPage;

import java.util.ArrayList;
import java.util.List;

public final class LayoutEngine {
    private static final float DEFAULT_FONT_SIZE = 12f;
    private static final float MINIMUM_LINE_HEIGHT = 1f;

    private final PdfDocument document;
    private final PageSize pageSize;
    private final float topMargin;
    private final float rightMargin;
    private final float bottomMargin;
    private final float leftMargin;
    private final TextContext defaultText;
    private PdfPage page;
    private float cursorTop;

    public LayoutEngine(PdfDocument document, PageSize pageSize, float topMargin, float rightMargin,
                        float bottomMargin, float leftMargin) {
        this(document, pageSize, topMargin, rightMargin, bottomMargin, leftMargin,
            PdfFontFactory.regular(), DEFAULT_FONT_SIZE);
    }

    public LayoutEngine(PdfDocument document, PageSize pageSize, float topMargin, float rightMargin,
                        float bottomMargin, float leftMargin, PdfFont defaultFont, float defaultFontSize) {
        this.document = document;
        this.pageSize = pageSize;
        this.topMargin = topMargin;
        this.rightMargin = rightMargin;
        this.bottomMargin = bottomMargin;
        this.leftMargin = leftMargin;
        this.defaultText = new TextContext(
            defaultFont, defaultFontSize, ColorConstants.BLACK, TextAlignment.LEFT, 1.2f
        );
    }

    public void render(List<LayoutElement> elements) {
        ensurePage();
        for (int index = 0; index < elements.size(); index++) {
            var element = elements.get(index);
            if (element.style().fixedPosition() != null) {
                renderFixed(element, element.style().fixedPosition(), 1f);
                continue;
            }
            if (element.style().keepWithNext() && index + 1 < elements.size()) {
                var required = estimate(element, contentWidth(), defaultText)
                    + estimate(elements.get(index + 1), contentWidth(), defaultText);
                if (required > remainingHeight() && required < contentHeight()) {
                    newPage();
                }
            }
            renderFlow(element, defaultText, leftMargin, contentWidth());
        }
    }

    public static void renderOverlay(PdfDocument document, PdfPage page, Rectangle bounds, LayoutElement element,
                                     float opacity) {
        var engine = new LayoutEngine(document, page.getPageSize(), 0, 0, 0, 0);
        engine.page = page;
        engine.cursorTop = bounds.getTop();
        var fixed = element.style().fixedPosition();
        if (fixed == null) {
            fixed = new Style.FixedPosition(0, bounds.getLeft(), bounds.getBottom(), bounds.getWidth());
        }
        engine.renderFixedOnPage(element, page, fixed.x(), fixed.y(), fixed.width(), opacity);
    }

    private void renderFlow(LayoutElement element, TextContext inherited, float x, float availableWidth) {
        switch (element) {
            case AreaBreak ignored -> newPage();
            case Paragraph paragraph -> renderFlowParagraph(paragraph, inherited, x, availableWidth);
            case Table table -> renderFlowTable(table, inherited, x, availableWidth);
            case Image image -> renderFlowImage(image, x, availableWidth);
            case LineSeparator separator -> renderFlowLine(separator, x, availableWidth);
            case Div div -> renderFlowDiv(div, inherited, x, availableWidth);
            default -> throw new IllegalArgumentException("Unsupported layout element: " + element.getClass().getName());
        }
    }

    private void renderFlowParagraph(Paragraph paragraph, TextContext inherited, float x, float availableWidth) {
        var style = paragraph.style();
        var blockWidth = resolveWidth(style.width(), availableWidth, availableWidth);
        blockWidth = Math.min(blockWidth, availableWidth - style.marginLeft() - style.marginRight());
        var blockX = alignedX(x + style.marginLeft(), availableWidth - style.marginLeft() - style.marginRight(),
            blockWidth, style.horizontalAlignment(HorizontalAlignment.LEFT));
        var contentWidth = Math.max(1f, blockWidth - style.paddingLeft() - style.paddingRight());
        var context = resolveTextContext(style, inherited);
        var layout = layoutParagraph(paragraph, contentWidth, context);
        var blockHeight = Math.max(floatOr(style.height(), 0),
            layout.height() + style.paddingTop() + style.paddingBottom());
        var required = style.marginTop() + blockHeight + style.marginBottom();
        if (required > remainingHeight() && cursorTop < pageSize.getHeight() - topMargin) {
            newPage();
        }
        var top = cursorTop - style.marginTop();
        drawBlockDecoration(style, blockX, top - blockHeight, blockWidth, blockHeight, 1f);
        renderLines(layout, blockX + style.paddingLeft(), top - style.paddingTop(), contentWidth, context.textAlignment(), 1f);
        cursorTop = top - blockHeight - style.marginBottom();
    }

    private void renderFlowImage(Image image, float x, float availableWidth) {
        var style = image.style();
        var width = Math.min(image.getImageScaledWidth(), availableWidth - style.marginLeft() - style.marginRight());
        var scale = width / image.getImageScaledWidth();
        var height = image.getImageScaledHeight() * scale;
        var required = style.marginTop() + height + style.marginBottom();
        if (required > remainingHeight() && cursorTop < pageSize.getHeight() - topMargin) {
            newPage();
        }
        var contentArea = availableWidth - style.marginLeft() - style.marginRight();
        var imageX = alignedX(x + style.marginLeft(), contentArea, width,
            style.horizontalAlignment(HorizontalAlignment.LEFT));
        var top = cursorTop - style.marginTop();
        PdfDrawing.image(document, page, image.source(), imageX, top - height, width, height);
        cursorTop = top - height - style.marginBottom();
    }

    private void renderFlowLine(LineSeparator separator, float x, float availableWidth) {
        var style = separator.style();
        var width = resolveWidth(style.width(), availableWidth, availableWidth);
        var top = cursorTop - style.marginTop();
        if (style.marginTop() + separator.line().lineWidth() + style.marginBottom() > remainingHeight()) {
            newPage();
            top = cursorTop - style.marginTop();
        }
        PdfDrawing.line(document, page, separator.line().color(), separator.line().lineWidth(), x, top,
            x + width, top);
        cursorTop = top - separator.line().lineWidth() - style.marginBottom();
    }

    private void renderFlowDiv(Div div, TextContext inherited, float x, float availableWidth) {
        var style = div.style();
        var width = resolveWidth(style.width(), availableWidth, availableWidth - style.marginLeft() - style.marginRight());
        var estimated = estimate(div, width, inherited);
        if (style.keepTogether() && estimated > remainingHeight() && estimated < contentHeight()) {
            newPage();
        }
        var top = cursorTop - style.marginTop();
        var decorationTop = top;
        cursorTop = top - style.paddingTop();
        var childX = x + style.marginLeft() + style.paddingLeft();
        var childWidth = Math.max(1f, width - style.paddingLeft() - style.paddingRight());
        var context = resolveTextContext(style, inherited);
        for (var child : div.children()) {
            renderFlow(child, context, childX, childWidth);
        }
        cursorTop -= style.paddingBottom();
        var renderedHeight = decorationTop - cursorTop;
        if (!Float.isNaN(style.height())) {
            renderedHeight = Math.max(renderedHeight, style.height());
            cursorTop = decorationTop - renderedHeight;
        }
        drawBlockDecoration(style, x + style.marginLeft(), decorationTop - renderedHeight, width, renderedHeight, 1f);
        cursorTop -= style.marginBottom();
    }

    private void renderFlowTable(Table table, TextContext inherited, float x, float availableWidth) {
        var style = table.style();
        var tableWidth = resolveWidth(style.width(), availableWidth,
            availableWidth - style.marginLeft() - style.marginRight());
        var tableX = alignedX(x + style.marginLeft(), availableWidth - style.marginLeft() - style.marginRight(),
            tableWidth, style.horizontalAlignment(HorizontalAlignment.LEFT));
        var columnWidths = resolvedColumnWidths(table.columnWidths(), tableWidth);
        var context = resolveTextContext(style, inherited);
        var headerRows = rows(table.headerCells(), table.numberOfColumns());
        var bodyRows = rows(table.cells(), table.numberOfColumns());
        cursorTop -= style.marginTop();
        if (!headerRows.isEmpty()) {
            var headerHeight = rowsHeight(headerRows, columnWidths, context);
            if (headerHeight > remainingHeight() && cursorTop < pageSize.getHeight() - topMargin) {
                newPage();
            }
            renderRows(headerRows, columnWidths, tableX, context, false, List.of());
        }
        for (var row : bodyRows) {
            var rowHeight = rowHeight(row, columnWidths, context);
            if (rowHeight > remainingHeight() && cursorTop < pageSize.getHeight() - topMargin) {
                newPage();
                if (!headerRows.isEmpty()) {
                    renderRows(headerRows, columnWidths, tableX, context, false, List.of());
                }
            }
            renderRow(row, columnWidths, tableX, context, rowHeight);
        }
        cursorTop -= style.marginBottom();
    }

    private void renderRows(List<Row> rows, float[] columnWidths, float x, TextContext context,
                            boolean repeat, List<Row> ignored) {
        for (var row : rows) {
            var height = rowHeight(row, columnWidths, context);
            renderRow(row, columnWidths, x, context, height);
        }
    }

    private void renderRow(Row row, float[] columnWidths, float tableX, TextContext inherited, float height) {
        var bottom = cursorTop - height;
        for (var placed : row.cells()) {
            var cell = placed.cell();
            var cellX = tableX + sum(columnWidths, 0, placed.column());
            var cellWidth = sum(columnWidths, placed.column(), placed.column() + cell.columnSpan());
            renderCell(cell, cellX, bottom, cellWidth, height, inherited);
        }
        cursorTop = bottom;
    }

    private void renderCell(Cell cell, float x, float bottom, float width, float height, TextContext inherited) {
        var style = cell.style();
        if (style.backgroundColor() != null) {
            PdfDrawing.fill(document, page, style.backgroundColor(), x, bottom, width, height, 1f);
        }
        PdfDrawing.borders(document, page, style.borderTop(), style.borderRight(), style.borderBottom(),
            style.borderLeft(), x, bottom, width, height);
        var context = resolveTextContext(style, inherited);
        var innerWidth = Math.max(1f, width - style.paddingLeft() - style.paddingRight());
        var contentHeight = cellContentHeight(cell, innerWidth, context);
        var top = switch (style.verticalAlignment(VerticalAlignment.TOP)) {
            case TOP -> bottom + height - style.paddingTop();
            case MIDDLE -> bottom + (height + contentHeight) / 2f;
            case BOTTOM -> bottom + style.paddingBottom() + contentHeight;
        };
        for (var child : cell.children()) {
            switch (child) {
                case Paragraph paragraph -> {
                    var childStyle = paragraph.style();
                    top -= childStyle.marginTop();
                    var paragraphContext = resolveTextContext(childStyle, context);
                    var paragraphWidth = Math.max(1f, innerWidth - childStyle.marginLeft() - childStyle.marginRight());
                    var layout = layoutParagraph(paragraph, paragraphWidth, paragraphContext);
                    renderLines(layout, x + style.paddingLeft() + childStyle.marginLeft(), top,
                        paragraphWidth, paragraphContext.textAlignment(), 1f);
                    top -= layout.height() + childStyle.marginBottom();
                }
                case Image image -> {
                    var imageWidth = Math.min(image.getImageScaledWidth(), innerWidth);
                    var scale = imageWidth / image.getImageScaledWidth();
                    var imageHeight = image.getImageScaledHeight() * scale;
                    PdfDrawing.image(document, page, image.source(), x + style.paddingLeft(), top - imageHeight,
                        imageWidth, imageHeight);
                    top -= imageHeight;
                }
                case LineSeparator separator -> {
                    PdfDrawing.line(document, page, separator.line().color(), separator.line().lineWidth(),
                        x + style.paddingLeft(), top, x + width - style.paddingRight(), top);
                    top -= separator.line().lineWidth();
                }
                case Div div -> top -= estimate(div, innerWidth, context);
                case Table table -> top -= estimate(table, innerWidth, context);
                default -> throw new IllegalArgumentException("Unsupported cell child: " + child.getClass().getName());
            }
        }
    }

    private float estimate(LayoutElement element, float width, TextContext inherited) {
        var style = element.style();
        return switch (element) {
            case Paragraph paragraph -> {
                var innerWidth = Math.max(1f, width - style.marginLeft() - style.marginRight()
                    - style.paddingLeft() - style.paddingRight());
                var layout = layoutParagraph(paragraph, innerWidth, resolveTextContext(style, inherited));
                yield style.marginTop() + style.paddingTop() + layout.height() + style.paddingBottom() + style.marginBottom();
            }
            case Image image -> style.marginTop() + image.getImageScaledHeight() + style.marginBottom();
            case LineSeparator separator -> style.marginTop() + separator.line().lineWidth() + style.marginBottom();
            case Div div -> {
                var childWidth = Math.max(1f, width - style.paddingLeft() - style.paddingRight());
                var context = resolveTextContext(style, inherited);
                var height = style.marginTop() + style.paddingTop() + style.paddingBottom() + style.marginBottom();
                for (var child : div.children()) {
                    height += estimate(child, childWidth, context);
                }
                yield Float.isNaN(style.height()) ? height : Math.max(height, style.height());
            }
            case Table table -> {
                var tableWidth = resolveWidth(style.width(), width, width);
                var columns = resolvedColumnWidths(table.columnWidths(), tableWidth);
                var context = resolveTextContext(style, inherited);
                yield style.marginTop() + rowsHeight(rows(table.headerCells(), table.numberOfColumns()), columns, context)
                    + rowsHeight(rows(table.cells(), table.numberOfColumns()), columns, context) + style.marginBottom();
            }
            case AreaBreak ignored -> 0f;
            default -> 0f;
        };
    }

    private float rowsHeight(List<Row> rows, float[] widths, TextContext context) {
        var height = 0f;
        for (var row : rows) {
            height += rowHeight(row, widths, context);
        }
        return height;
    }

    private float rowHeight(Row row, float[] columnWidths, TextContext inherited) {
        var height = 0f;
        for (var placed : row.cells()) {
            var width = sum(columnWidths, placed.column(), placed.column() + placed.cell().columnSpan());
            height = Math.max(height, measureCell(placed.cell(), width, inherited));
        }
        return Math.max(1f, height);
    }

    private float measureCell(Cell cell, float width, TextContext inherited) {
        var style = cell.style();
        var innerWidth = Math.max(1f, width - style.paddingLeft() - style.paddingRight());
        var content = cellContentHeight(cell, innerWidth, resolveTextContext(style, inherited));
        return Math.max(floatOr(style.height(), 0), style.paddingTop() + content + style.paddingBottom());
    }

    private float cellContentHeight(Cell cell, float innerWidth, TextContext context) {
        var height = 0f;
        for (var child : cell.children()) {
            height += estimate(child, innerWidth, context);
        }
        return height;
    }

    private ParagraphLayout layoutParagraph(Paragraph paragraph, float width, TextContext inherited) {
        var tokens = tokens(paragraph, inherited);
        var lines = new ArrayList<TextLine>();
        var builder = new LineBuilder();
        for (var token : tokens) {
            if ("\n".equals(token.value())) {
                lines.add(builder.finish(inherited));
                builder = new LineBuilder();
                continue;
            }
            var tokenWidth = token.font().getWidth(token.value(), token.fontSize());
            if (!builder.empty() && builder.width() + tokenWidth > width && !token.value().isBlank()) {
                lines.add(builder.finish(inherited));
                builder = new LineBuilder();
            }
            if (tokenWidth > width && !token.value().isBlank()) {
                for (int offset = 0; offset < token.value().length(); offset++) {
                    var character = token.value().substring(offset, offset + 1);
                    var characterWidth = token.font().getWidth(character, token.fontSize());
                    if (!builder.empty() && builder.width() + characterWidth > width) {
                        lines.add(builder.finish(inherited));
                        builder = new LineBuilder();
                    }
                    builder.add(new ResolvedText(character, token.font(), token.fontSize(), token.color()), characterWidth);
                }
            } else if (!builder.empty() || !token.value().isBlank()) {
                builder.add(token, tokenWidth);
            }
        }
        if (!builder.empty() || lines.isEmpty()) {
            lines.add(builder.finish(inherited));
        }
        var height = 0f;
        for (var line : lines) {
            height += line.height();
        }
        return new ParagraphLayout(List.copyOf(lines), height);
    }

    private List<ResolvedText> tokens(Paragraph paragraph, TextContext inherited) {
        var paragraphContext = resolveTextContext(paragraph.style(), inherited);
        var result = new ArrayList<ResolvedText>();
        for (var run : paragraph.textRuns()) {
            var context = resolveTextContext(run.style(), paragraphContext);
            var value = run.value();
            var current = new StringBuilder();
            var whitespace = false;
            for (int index = 0; index < value.length(); index++) {
                var character = value.charAt(index);
                if (character == '\n') {
                    flushToken(result, current, context);
                    result.add(new ResolvedText("\n", context.font(), context.fontSize(), context.color()));
                    whitespace = false;
                    continue;
                }
                var nextWhitespace = Character.isWhitespace(character);
                if (current.length() > 0 && nextWhitespace != whitespace) {
                    flushToken(result, current, context);
                }
                whitespace = nextWhitespace;
                current.append(character);
            }
            flushToken(result, current, context);
        }
        return result;
    }

    private static void flushToken(List<ResolvedText> target, StringBuilder value, TextContext context) {
        if (value.length() > 0) {
            target.add(new ResolvedText(value.toString(), context.font(), context.fontSize(), context.color()));
            value.setLength(0);
        }
    }

    private void renderLines(ParagraphLayout layout, float x, float top, float width, TextAlignment alignment,
                             float opacity) {
        var lineTop = top;
        for (var line : layout.lines()) {
            var lineX = switch (alignment) {
                case LEFT -> x;
                case CENTER -> x + (width - line.width()) / 2f;
                case RIGHT -> x + width - line.width();
            };
            var fragmentX = lineX;
            for (var fragment : line.fragments()) {
                var baseline = lineTop - fragment.fontSize();
                PdfDrawing.text(document, page, fragment.value(), fragment.font(), fragment.fontSize(), fragment.color(),
                    fragmentX, baseline, fragment.width(), TextAlignment.LEFT, 0, opacity);
                fragmentX += fragment.width();
            }
            lineTop -= line.height();
        }
    }

    private void renderFixed(LayoutElement element, Style.FixedPosition fixed, float opacity) {
        var targetPageNumber = fixed.pageNumber() <= 0 ? Math.max(1, document.getNumberOfPages()) : fixed.pageNumber();
        while (document.getNumberOfPages() < targetPageNumber) {
            document.addNewPage(pageSize);
        }
        renderFixedOnPage(element, document.getPage(targetPageNumber), fixed.x(), fixed.y(), fixed.width(), opacity);
    }

    private void renderFixedOnPage(LayoutElement element, PdfPage targetPage, float x, float y, float width,
                                   float opacity) {
        var previousPage = page;
        page = targetPage;
        switch (element) {
            case AreaBreak ignored -> newPage();
            case Paragraph paragraph -> {
                var style = paragraph.style();
                var context = resolveTextContext(style, defaultText);
                var contentWidth = Math.max(1f, width - style.paddingLeft() - style.paddingRight());
                var layout = layoutParagraph(paragraph, contentWidth, context);
                var height = Math.max(floatOr(style.height(), 0), layout.height() + style.paddingTop() + style.paddingBottom());
                drawBlockDecoration(style, x, y, width, height, opacity);
                renderLines(layout, x + style.paddingLeft(), y + height - style.paddingTop(), contentWidth,
                    context.textAlignment(), opacity);
            }
            case Div div -> {
                var style = div.style();
                var height = Math.max(floatOr(style.height(), 0), estimate(div, width, defaultText));
                drawBlockDecoration(style, x, y, width, height, opacity);
            }
            case Image image -> PdfDrawing.image(document, targetPage, image.source(), x, y,
                image.getImageScaledWidth(), image.getImageScaledHeight());
            case LineSeparator separator -> PdfDrawing.line(document, targetPage, separator.line().color(),
                separator.line().lineWidth(), x, y, x + width, y);
            case Table table -> {
                var savedTop = cursorTop;
                cursorTop = y + estimate(table, width, defaultText);
                renderFlowTable(table, defaultText, x, width);
                cursorTop = savedTop;
            }
            default -> throw new IllegalArgumentException("Unsupported fixed element: " + element.getClass().getName());
        }
        page = previousPage;
    }

    private void drawBlockDecoration(Style style, float x, float y, float width, float height, float opacity) {
        if (style.backgroundColor() != null) {
            PdfDrawing.fill(document, page, style.backgroundColor(), x, y, width, height, opacity);
        }
        PdfDrawing.borders(document, page, style.borderTop(), style.borderRight(), style.borderBottom(),
            style.borderLeft(), x, y, width, height);
    }

    private TextContext resolveTextContext(Style style, TextContext inherited) {
        var font = style.font() == null ? inherited.font() : style.font();
        if (style.simulatedBold()) {
            font = PdfFontFactory.bold();
        }
        return new TextContext(
            font,
            style.fontSize(inherited.fontSize()),
            style.fontColor(inherited.color()),
            style.textAlignment(inherited.textAlignment()),
            style.multipliedLeading()
        );
    }

    private static List<Row> rows(List<Cell> cells, int numberOfColumns) {
        var rows = new ArrayList<Row>();
        var current = new ArrayList<PlacedCell>();
        var column = 0;
        for (var cell : cells) {
            if (column + cell.columnSpan() > numberOfColumns && !current.isEmpty()) {
                rows.add(new Row(List.copyOf(current)));
                current.clear();
                column = 0;
            }
            current.add(new PlacedCell(cell, column));
            column += cell.columnSpan();
            if (column >= numberOfColumns) {
                rows.add(new Row(List.copyOf(current)));
                current.clear();
                column = 0;
            }
        }
        if (!current.isEmpty()) {
            rows.add(new Row(List.copyOf(current)));
        }
        return List.copyOf(rows);
    }

    private static float[] resolvedColumnWidths(float[] weights, float tableWidth) {
        var total = 0f;
        for (float weight : weights) {
            total += weight;
        }
        var result = new float[weights.length];
        for (int index = 0; index < weights.length; index++) {
            result[index] = tableWidth * weights[index] / total;
        }
        return result;
    }

    private static float sum(float[] values, int from, int to) {
        var result = 0f;
        for (int index = from; index < Math.min(to, values.length); index++) {
            result += values[index];
        }
        return result;
    }

    private static float alignedX(float left, float available, float width, HorizontalAlignment alignment) {
        return switch (alignment) {
            case LEFT -> left;
            case CENTER -> left + (available - width) / 2f;
            case RIGHT -> left + available - width;
        };
    }

    private static float resolveWidth(UnitValue unitValue, float available, float fallback) {
        if (unitValue == null) {
            return fallback;
        }
        return switch (unitValue.unitType()) {
            case POINT -> unitValue.value();
            case PERCENT -> available * unitValue.value() / 100f;
            case POINT_ARRAY, PERCENT_ARRAY -> fallback;
        };
    }

    private static float floatOr(float value, float fallback) {
        return Float.isNaN(value) ? fallback : value;
    }

    private void ensurePage() {
        if (page == null) {
            page = document.addNewPage(pageSize);
            cursorTop = pageSize.getHeight() - topMargin;
        }
    }

    private void newPage() {
        page = document.addNewPage(pageSize);
        cursorTop = pageSize.getHeight() - topMargin;
    }

    private float contentWidth() {
        return pageSize.getWidth() - leftMargin - rightMargin;
    }

    private float contentHeight() {
        return pageSize.getHeight() - topMargin - bottomMargin;
    }

    private float remainingHeight() {
        return cursorTop - bottomMargin;
    }

    private record TextContext(PdfFont font, float fontSize, Color color, TextAlignment textAlignment,
                               float multipliedLeading) {
    }

    private record ResolvedText(String value, PdfFont font, float fontSize, Color color) {
    }

    private record TextFragment(String value, PdfFont font, float fontSize, Color color, float width) {
    }

    private record TextLine(List<TextFragment> fragments, float width, float height) {
    }

    private record ParagraphLayout(List<TextLine> lines, float height) {
    }

    private record PlacedCell(Cell cell, int column) {
    }

    private record Row(List<PlacedCell> cells) {
    }

    private static final class LineBuilder {
        private final List<TextFragment> fragments = new ArrayList<>();
        private float width;
        private float maximumFontSize;

        void add(ResolvedText text, float textWidth) {
            fragments.add(new TextFragment(text.value(), text.font(), text.fontSize(), text.color(), textWidth));
            width += textWidth;
            maximumFontSize = Math.max(maximumFontSize, text.fontSize());
        }

        boolean empty() {
            return fragments.isEmpty();
        }

        float width() {
            return width;
        }

        TextLine finish(TextContext context) {
            var height = Math.max(MINIMUM_LINE_HEIGHT,
                (maximumFontSize == 0 ? context.fontSize() : maximumFontSize) * context.multipliedLeading());
            return new TextLine(List.copyOf(fragments), width, height);
        }
    }
}
