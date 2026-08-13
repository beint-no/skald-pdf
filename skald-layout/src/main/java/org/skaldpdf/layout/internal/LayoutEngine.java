package org.skaldpdf.layout.internal;

import org.skaldpdf.colors.Color;
import org.skaldpdf.colors.ColorConstants;
import org.skaldpdf.font.PdfFont;
import org.skaldpdf.font.PdfFontFactory;
import org.skaldpdf.geom.PageSize;
import org.skaldpdf.geom.Rectangle;
import org.skaldpdf.layout.PageNumbering;
import org.skaldpdf.layout.Style;
import org.skaldpdf.layout.element.AreaBreak;
import org.skaldpdf.layout.element.Cell;
import org.skaldpdf.layout.element.Div;
import org.skaldpdf.layout.element.Image;
import org.skaldpdf.layout.element.LayoutElement;
import org.skaldpdf.layout.element.LineSeparator;
import org.skaldpdf.layout.element.ListBlock;
import org.skaldpdf.layout.element.Paragraph;
import org.skaldpdf.layout.element.Table;
import org.skaldpdf.layout.element.Text;
import org.skaldpdf.layout.properties.HorizontalAlignment;
import org.skaldpdf.layout.properties.OverflowWrap;
import org.skaldpdf.layout.properties.TextAlignment;
import org.skaldpdf.layout.properties.UnitValue;
import org.skaldpdf.layout.properties.VerticalAlignment;
import org.skaldpdf.pdf.PdfDocument;
import org.skaldpdf.pdf.PdfPage;

import java.util.ArrayList;
import java.util.List;

public final class LayoutEngine {
    private static final float DEFAULT_FONT_SIZE = 12f;
    private static final float DEFAULT_LEADING = 1.35f;
    private static final float MINIMUM_LINE_HEIGHT = 1f;

    private final PdfDocument document;
    private PageSize pageSize;
    private final float topMargin;
    private final float rightMargin;
    private final float bottomMargin;
    private final float leftMargin;
    private final float headerHeight;
    private final float firstHeaderHeight;
    private final float footerHeight;
    private final TextContext defaultText;
    private PdfPage page;
    private float cursorTop;
    private boolean allowPageBreaks = true;

    public LayoutEngine(PdfDocument document, PageSize pageSize, float topMargin, float rightMargin,
                        float bottomMargin, float leftMargin) {
        this(document, pageSize, topMargin, rightMargin, bottomMargin, leftMargin,
            PdfFontFactory.regular(), DEFAULT_FONT_SIZE, 0, 0);
    }

    public LayoutEngine(PdfDocument document, PageSize pageSize, float topMargin, float rightMargin,
                        float bottomMargin, float leftMargin, PdfFont defaultFont, float defaultFontSize) {
        this(document, pageSize, topMargin, rightMargin, bottomMargin, leftMargin,
            defaultFont, defaultFontSize, 0, 0, 0);
    }

    public LayoutEngine(PdfDocument document, PageSize pageSize, float topMargin, float rightMargin,
                        float bottomMargin, float leftMargin, PdfFont defaultFont, float defaultFontSize,
                        float headerHeight, float footerHeight) {
        this(document, pageSize, topMargin, rightMargin, bottomMargin, leftMargin,
            defaultFont, defaultFontSize, headerHeight, footerHeight, 0);
    }

    public LayoutEngine(PdfDocument document, PageSize pageSize, float topMargin, float rightMargin,
                        float bottomMargin, float leftMargin, PdfFont defaultFont, float defaultFontSize,
                        float headerHeight, float footerHeight, float firstHeaderHeight) {
        this(document, pageSize, topMargin, rightMargin, bottomMargin, leftMargin,
            defaultFont, defaultFontSize, headerHeight, footerHeight, firstHeaderHeight, List.of());
    }

    public LayoutEngine(PdfDocument document, PageSize pageSize, float topMargin, float rightMargin,
                        float bottomMargin, float leftMargin, PdfFont defaultFont, float defaultFontSize,
                        float headerHeight, float footerHeight, float firstHeaderHeight,
                        List<PdfFont> fallbacks) {
        this.document = document;
        this.pageSize = pageSize;
        this.topMargin = topMargin;
        this.rightMargin = rightMargin;
        this.bottomMargin = bottomMargin;
        this.leftMargin = leftMargin;
        this.headerHeight = headerHeight;
        this.firstHeaderHeight = firstHeaderHeight;
        this.footerHeight = footerHeight;
        this.defaultText = new TextContext(
            defaultFont, defaultFontSize, ColorConstants.INK, TextAlignment.LEFT, DEFAULT_LEADING, false, false,
            List.copyOf(fallbacks)
        );
    }

    public void render(List<LayoutElement> elements) {
        for (int index = 0; index < elements.size(); index++) {
            render(elements.get(index), index + 1 < elements.size() ? elements.get(index + 1) : null);
        }
    }

    public void render(LayoutElement element, LayoutElement next) {
        ensurePage();
        if (element.style().fixedPosition() != null) {
            renderFixed(element, element.style().fixedPosition(), 1f);
            return;
        }
        if (element.style().keepWithNext() && next != null) {
            var required = estimate(element, contentWidth(), defaultText)
                + estimate(next, contentWidth(), defaultText);
            if (required > remainingHeight() && required <= contentHeight()) {
                newPage();
            }
        }
        renderFlow(element, defaultText, leftMargin, contentWidth());
    }

    public void finishPages(java.util.function.Function<PageNumbering, LayoutElement> header,
                            java.util.function.Function<PageNumbering, LayoutElement> firstHeader,
                            java.util.function.Function<PageNumbering, LayoutElement> footer) {
        var count = document.getNumberOfPages();
        if (count == 0) {
            return;
        }
        var previousBreaks = allowPageBreaks;
        allowPageBreaks = false;
        try {
            for (int pageNumber = 1; pageNumber <= count; pageNumber++) {
                var numbering = new PageNumbering(pageNumber, count);
                var target = document.getPage(pageNumber);
                var chrome = pageNumber == 1 && firstHeader != null ? firstHeader : header;
                var band = pageNumber == 1 && firstHeaderHeight > 0 ? firstHeaderHeight : headerHeight;
                if (chrome != null && band > 0) {
                    var element = chrome.apply(numbering);
                    if (element != null) {
                        var estimated = Math.min(band,
                            Math.max(8f, estimate(element, contentWidth(), defaultText)));
                        renderFixedOnPage(element, target, leftMargin,
                            target.getPageSize().getHeight() - topMargin - estimated, contentWidth(), 1f);
                    }
                }
                if (footer != null && footerHeight > 0) {
                    var element = footer.apply(numbering);
                    if (element != null) {
                        renderFixedOnPage(element, target, leftMargin, bottomMargin, contentWidth(), 1f);
                    }
                }
            }
        } finally {
            allowPageBreaks = previousBreaks;
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
            case AreaBreak areaBreak -> {
                if (areaBreak.nextPageSize() != null) {
                    pageSize = areaBreak.nextPageSize();
                }
                newPage();
            }
            case Paragraph paragraph -> renderFlowParagraph(paragraph, inherited, x, availableWidth);
            case Table table -> renderFlowTable(table, inherited, x, availableWidth);
            case Image image -> renderFlowImage(image, x, availableWidth);
            case LineSeparator separator -> renderFlowLine(separator, x, availableWidth);
            case Div div -> renderFlowDiv(div, inherited, x, availableWidth);
            case ListBlock list -> renderFlowList(list, inherited, x, availableWidth);
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
        if ((style.keepTogether() || required > remainingHeight()) && required <= contentHeight()
            && required > remainingHeight()
            && cursorTop < pageSize.getHeight() - topInset()) {
            newPage();
        }
        if (required > remainingHeight() && required > contentHeight()) {
            renderParagraphFragments(layout, style, blockX, contentWidth, context);
            return;
        }
        var top = cursorTop - style.marginTop();
        drawBlockDecoration(style, blockX, top - blockHeight, blockWidth, blockHeight, 1f);
        renderLines(layout, blockX + style.paddingLeft(), top - style.paddingTop(), contentWidth, context.textAlignment(), 1f, context.underline(), context.strikethrough());
        annotate(style, blockX, top - blockHeight, blockWidth, blockHeight);
        cursorTop = top - blockHeight - style.marginBottom();
    }

    private void renderParagraphFragments(ParagraphLayout layout, Style style, float blockX,
                                          float contentWidth, TextContext context) {
        var lineIndex = 0;
        var firstFragment = true;
        while (lineIndex < layout.lines().size()) {
            var topMarginForFragment = firstFragment ? style.marginTop() : 0f;
            var available = remainingHeight() - topMarginForFragment - style.paddingTop() - style.paddingBottom();
            if (available <= 0 && cursorTop < pageSize.getHeight() - topInset()) {
                newPage();
                continue;
            }
            var end = lineIndex;
            var lineHeight = 0f;
            while (end < layout.lines().size()
                && lineHeight + layout.lines().get(end).height() <= available) {
                lineHeight += layout.lines().get(end).height();
                end++;
            }
            if (end == lineIndex) {
                if (cursorTop < pageSize.getHeight() - topInset()) {
                    newPage();
                    continue;
                }
                throw new IllegalArgumentException("A paragraph line is taller than the page content area");
            }
            var lastFragment = end == layout.lines().size();
            var bottomMarginForFragment = lastFragment ? style.marginBottom() : 0f;
            var fragmentHeight = style.paddingTop() + lineHeight + style.paddingBottom();
            var top = cursorTop - topMarginForFragment;
            drawBlockDecoration(style, blockX, top - fragmentHeight,
                contentWidth + style.paddingLeft() + style.paddingRight(), fragmentHeight, 1f);
            var fragment = new ParagraphLayout(List.copyOf(layout.lines().subList(lineIndex, end)), lineHeight);
            renderLines(fragment, blockX + style.paddingLeft(), top - style.paddingTop(),
                contentWidth, context.textAlignment(), 1f, context.underline(), context.strikethrough());
            annotate(style, blockX, top - fragmentHeight,
                contentWidth + style.paddingLeft() + style.paddingRight(), fragmentHeight);
            cursorTop = top - fragmentHeight - bottomMarginForFragment;
            lineIndex = end;
            firstFragment = false;
            if (!lastFragment) {
                newPage();
            }
        }
    }

    private void renderFlowImage(Image image, float x, float availableWidth) {
        var style = image.style();
        var width = Math.min(image.getImageScaledWidth(), availableWidth - style.marginLeft() - style.marginRight());
        var scale = width / image.getImageScaledWidth();
        var height = image.getImageScaledHeight() * scale;
        var required = style.marginTop() + height + style.marginBottom();
        if (required > remainingHeight() && cursorTop < pageSize.getHeight() - topInset()) {
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

    private void renderFlowList(ListBlock list, TextContext inherited, float x, float availableWidth) {
        var style = list.style();
        var width = resolveWidth(style.width(), availableWidth, availableWidth - style.marginLeft() - style.marginRight());
        var estimated = estimate(list, width, inherited);
        if (style.keepTogether() && estimated > remainingHeight() && estimated <= contentHeight()) {
            newPage();
        }
        cursorTop -= style.marginTop() + style.paddingTop();
        var context = resolveTextContext(style, inherited);
        var childX = x + style.marginLeft() + style.paddingLeft();
        var markerWidth = list.markerColumnWidth();
        var childWidth = Math.max(1f, width - style.paddingLeft() - style.paddingRight() - markerWidth);
        var index = 1;
        for (var item : list.items()) {
            var itemHeight = estimate(item, childWidth, context);
            if (itemHeight > remainingHeight() && cursorTop < pageSize.getHeight() - topInset()) {
                newPage();
            }
            var itemTop = cursorTop;
            drawListMarker(list.marker(), index++, childX, itemTop, markerWidth, context);
            renderFlow(item, context, childX + markerWidth, childWidth);
        }
        cursorTop -= style.paddingBottom() + style.marginBottom();
    }

    private void drawListMarker(ListBlock.Marker marker, int index, float x, float top, float width,
                                TextContext context) {
        var baseline = top - context.font().ascent(context.fontSize());
        switch (marker) {
            case DISC -> PdfDrawing.disc(document, page, context.color(),
                x + width - 6f, baseline + context.fontSize() * 0.32f, 1.7f);
            case DASH -> PdfDrawing.line(document, page, context.color(), 1f,
                x + 2f, baseline + context.fontSize() * 0.3f, x + width - 4f, baseline + context.fontSize() * 0.3f);
            case DECIMAL -> PdfDrawing.text(document, page, index + ".", context.font(), context.fontSize(),
                context.color(), x, baseline, width - 3f, TextAlignment.RIGHT, 0, 1f);
        }
    }

    private void renderFlowDiv(Div div, TextContext inherited, float x, float availableWidth) {
        var style = div.style();
        var width = resolveWidth(style.width(), availableWidth, availableWidth - style.marginLeft() - style.marginRight());
        var estimated = estimate(div, width, inherited);
        var decorated = hasDecoration(style);
        var decorationHeight = estimated - style.marginTop() - style.marginBottom();
        if ((style.keepTogether() || decorated) && estimated > remainingHeight() && estimated <= contentHeight()) {
            newPage();
        }
        if (decorated && decorationHeight > contentHeight()) {
            throw new IllegalArgumentException(
                "A decorated flow Div must fit on one page; split the content into smaller Div elements");
        }
        var top = cursorTop - style.marginTop();
        var decorationTop = top;
        drawBackground(style, x + style.marginLeft(), decorationTop - decorationHeight, width, decorationHeight, 1f);
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
        PdfDrawing.borders(document, page, style.borderTop(), style.borderRight(), style.borderBottom(),
            style.borderLeft(), x + style.marginLeft(), decorationTop - renderedHeight, width, renderedHeight,
            style.borderRadius());
        annotate(style, x + style.marginLeft(), decorationTop - renderedHeight, width, renderedHeight);
        cursorTop -= style.marginBottom();
    }

    private static boolean hasDecoration(Style style) {
        return style.backgroundColor() != null || style.backgroundGradient() != null
            || style.borderTop() != null || style.borderRight() != null
            || style.borderBottom() != null || style.borderLeft() != null;
    }

    private void renderFlowTable(Table table, TextContext inherited, float x, float availableWidth) {
        var style = table.style();
        var estimated = estimate(table, availableWidth, inherited);
        if (style.keepTogether() && estimated > remainingHeight() && estimated <= contentHeight()) {
            newPage();
        }
        var tableWidth = resolveWidth(style.width(), availableWidth,
            availableWidth - style.marginLeft() - style.marginRight());
        var tableX = alignedX(x + style.marginLeft(), availableWidth - style.marginLeft() - style.marginRight(),
            tableWidth, style.horizontalAlignment(HorizontalAlignment.LEFT));
        var columnWidths = resolvedColumnWidths(table, tableWidth);
        var context = resolveTextContext(style, inherited);
        var headerRows = rows(table.headerCells(), table.numberOfColumns());
        var bodyRows = rows(table.cells(), table.numberOfColumns());
        var footerRows = rows(table.footerCells(), table.numberOfColumns());
        var headerHeights = rowHeights(headerRows, columnWidths, context);
        var bodyHeights = rowHeights(bodyRows, columnWidths, context);
        var footerHeights = rowHeights(footerRows, columnWidths, context);
        cursorTop -= style.marginTop();
        var headerHeight = sum(headerHeights, 0, headerHeights.length);
        var footerHeight = sum(footerHeights, 0, footerHeights.length);
        if (headerHeight + footerHeight > contentHeight()) {
            throw new IllegalArgumentException("Table headers and footers are taller than the page content area");
        }
        if (!headerRows.isEmpty()) {
            if (headerHeight > remainingHeight() && cursorTop < pageSize.getHeight() - topInset()) {
                newPage();
            }
            renderRows(headerRows, headerHeights, columnWidths, tableX, context);
        }
        for (int index = 0; index < bodyRows.size(); index++) {
            var row = bodyRows.get(index);
            var rowHeight = bodyHeights[index];
            var spanHeight = rowSpanBlockHeight(row, index, bodyHeights);
            var needed = spanHeight + footerHeight;
            if (needed > remainingHeight()
                && needed <= contentHeight() - headerHeight
                && cursorTop < pageSize.getHeight() - topInset()) {
                renderRows(footerRows, footerHeights, columnWidths, tableX, context);
                newPage();
                if (!headerRows.isEmpty()) {
                    renderRows(headerRows, headerHeights, columnWidths, tableX, context);
                }
            }
            if (rowHeight <= remainingHeight()) {
                renderRow(row, columnWidths, tableX, context, rowHeight, index, bodyHeights);
            } else {
                renderSplitRow(row, columnWidths, tableX, context, rowHeight, headerRows, headerHeights);
            }
        }
        renderRows(footerRows, footerHeights, columnWidths, tableX, context);
        cursorTop -= style.marginBottom();
    }

    private void renderRows(List<Row> rows, float[] heights, float[] columnWidths, float x, TextContext context) {
        for (int index = 0; index < rows.size(); index++) {
            renderRow(rows.get(index), columnWidths, x, context, heights[index], index, heights);
        }
    }

    private void renderRow(Row row, float[] columnWidths, float tableX, TextContext inherited, float height,
                           int rowIndex, float[] rowHeights) {
        var bottom = cursorTop - height;
        for (var placed : row.cells()) {
            var cell = placed.cell();
            var cellX = tableX + sum(columnWidths, 0, placed.column());
            var cellWidth = sum(columnWidths, placed.column(), placed.column() + cell.columnSpan());
            var cellHeight = cell.rowSpan() <= 1 ? height
                : sum(rowHeights, rowIndex, Math.min(rowHeights.length, rowIndex + cell.rowSpan()));
            renderCell(cell, cellX, cursorTop - cellHeight, cellWidth, cellHeight, inherited);
        }
        cursorTop = bottom;
    }

    private void renderSplitRow(Row row, float[] columnWidths, float tableX, TextContext inherited,
                                float totalHeight, List<Row> headerRows, float[] headerHeights) {
        var consumed = 0f;
        while (consumed < totalHeight) {
            if (remainingHeight() <= 0) {
                newPage();
                if (!headerRows.isEmpty()) {
                    renderRows(headerRows, headerHeights, columnWidths, tableX, inherited);
                }
            }
            var chunkHeight = Math.min(totalHeight - consumed, remainingHeight());
            var bottom = cursorTop - chunkHeight;
            for (var placed : row.cells()) {
                var cell = placed.cell();
                var cellX = tableX + sum(columnWidths, 0, placed.column());
                var cellWidth = sum(columnWidths, placed.column(), placed.column() + cell.columnSpan());
                renderCellDecoration(cell, cellX, bottom, cellWidth, chunkHeight);
                PdfDrawing.beginClip(document, page, cellX, bottom, cellWidth, chunkHeight);
                renderCellContent(cell, cellX, cursorTop + consumed - totalHeight, cellWidth,
                    totalHeight, inherited);
                PdfDrawing.endGraphicsState(document, page);
            }
            cursorTop = bottom;
            consumed += chunkHeight;
            if (consumed < totalHeight) {
                newPage();
                if (!headerRows.isEmpty()) {
                    renderRows(headerRows, headerHeights, columnWidths, tableX, inherited);
                }
            }
        }
    }

    private void renderCell(Cell cell, float x, float bottom, float width, float height, TextContext inherited) {
        renderCellDecoration(cell, x, bottom, width, height);
        renderCellContent(cell, x, bottom, width, height, inherited);
    }

    private void renderCellDecoration(Cell cell, float x, float bottom, float width, float height) {
        var style = cell.style();
        drawBackground(style, x, bottom, width, height, 1f);
        PdfDrawing.borders(document, page, style.borderTop(), style.borderRight(), style.borderBottom(),
            style.borderLeft(), x, bottom, width, height, style.borderRadius());
    }

    private void renderCellContent(Cell cell, float x, float bottom, float width, float height,
                                   TextContext inherited) {
        var style = cell.style();
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
                        paragraphWidth, paragraphContext.textAlignment(), 1f,
                        paragraphContext.underline(), paragraphContext.strikethrough());
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
                case Div div -> top = renderNested(div, context, x + style.paddingLeft(), top, innerWidth);
                case Table nested -> top = renderNested(nested, context, x + style.paddingLeft(), top, innerWidth);
                case ListBlock list -> top = renderNested(list, context, x + style.paddingLeft(), top, innerWidth);
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
            case Image image -> {
                var fitted = fittedImageSize(image, width - style.marginLeft() - style.marginRight());
                yield style.marginTop() + fitted[1] + style.marginBottom();
            }
            case LineSeparator separator -> style.marginTop() + separator.line().lineWidth() + style.marginBottom();
            case ListBlock list -> {
                var childWidth = Math.max(1f, width - style.paddingLeft() - style.paddingRight()
                    - list.markerColumnWidth());
                var context = resolveTextContext(style, inherited);
                var height = style.marginTop() + style.paddingTop() + style.paddingBottom() + style.marginBottom();
                for (var item : list.items()) {
                    height += estimate(item, childWidth, context);
                }
                yield height;
            }
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
                var columns = resolvedColumnWidths(table, tableWidth);
                var context = resolveTextContext(style, inherited);
                yield style.marginTop()
                    + sum(rowHeights(rows(table.headerCells(), table.numberOfColumns()), columns, context))
                    + sum(rowHeights(rows(table.cells(), table.numberOfColumns()), columns, context))
                    + sum(rowHeights(rows(table.footerCells(), table.numberOfColumns()), columns, context))
                    + style.marginBottom();
            }
            case AreaBreak ignored -> 0f;
            default -> 0f;
        };
    }

    private float[] rowHeights(List<Row> rows, float[] columnWidths, TextContext inherited) {
        var heights = new float[rows.size()];
        for (int index = 0; index < rows.size(); index++) {
            var height = 0f;
            for (var placed : rows.get(index).cells()) {
                if (placed.cell().rowSpan() > 1) {
                    continue;
                }
                var width = sum(columnWidths, placed.column(), placed.column() + placed.cell().columnSpan());
                height = Math.max(height, measureCell(placed.cell(), width, inherited));
            }
            heights[index] = Math.max(1f, height);
        }
        for (int index = 0; index < rows.size(); index++) {
            for (var placed : rows.get(index).cells()) {
                var span = Math.min(placed.cell().rowSpan(), rows.size() - index);
                if (span <= 1) {
                    continue;
                }
                var width = sum(columnWidths, placed.column(), placed.column() + placed.cell().columnSpan());
                var needed = measureCell(placed.cell(), width, inherited);
                var actual = sum(heights, index, index + span);
                if (needed > actual) {
                    heights[index + span - 1] += needed - actual;
                }
            }
        }
        return heights;
    }

    private static float rowSpanBlockHeight(Row row, int rowIndex, float[] heights) {
        var height = heights[rowIndex];
        for (var placed : row.cells()) {
            var span = Math.min(placed.cell().rowSpan(), heights.length - rowIndex);
            height = Math.max(height, sum(heights, rowIndex, rowIndex + span));
        }
        return height;
    }

    private static float sum(float[] values) {
        return sum(values, 0, values.length);
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
                lines.add(builder.finish(inherited, true));
                builder = new LineBuilder();
                continue;
            }
            var tokenWidth = token.font().getWidth(token.value(), token.fontSize());
            if (paragraph.style().overflowWrap() == OverflowWrap.ANYWHERE && !token.value().isBlank()) {
                builder = addByCodePoint(token, width, inherited, lines, builder);
                continue;
            }
            if (!builder.empty() && builder.width() + tokenWidth > width && !token.value().isBlank()) {
                lines.add(builder.finish(inherited, false));
                builder = new LineBuilder();
            }
            if (tokenWidth > width && !token.value().isBlank()) {
                builder = addByCodePoint(token, width, inherited, lines, builder);
            } else if (!builder.empty() || !token.value().isBlank()) {
                builder.add(token, tokenWidth);
            }
        }
        if (!builder.empty() || lines.isEmpty()) {
            lines.add(builder.finish(inherited, false));
        }
        var height = 0f;
        for (var line : lines) {
            height += line.height();
        }
        return new ParagraphLayout(List.copyOf(lines), height);
    }

    private static LineBuilder addByCodePoint(ResolvedText token, float width, TextContext inherited,
                                              List<TextLine> lines, LineBuilder builder) {
        var codePoints = token.value().codePoints().toArray();
        for (var codePoint : codePoints) {
            var character = new String(Character.toChars(codePoint));
            var characterWidth = token.font().getWidth(character, token.fontSize());
            if (!builder.empty() && builder.width() + characterWidth > width) {
                lines.add(builder.finish(inherited, false));
                builder = new LineBuilder();
            }
            builder.add(new ResolvedText(character, token.font(), token.fontSize(), token.color()), characterWidth);
        }
        return builder;
    }

    private List<ResolvedText> tokens(Paragraph paragraph, TextContext inherited) {
        var paragraphContext = resolveTextContext(paragraph.style(), inherited);
        var result = new ArrayList<ResolvedText>();
        for (var run : paragraph.textRuns()) {
            var context = resolveTextContext(run.style(), paragraphContext);
            var current = new StringBuilder();
            var whitespace = false;
            PdfFont tokenFont = context.font();
            for (var codePoint : run.value().codePoints().toArray()) {
                if (codePoint == '\n') {
                    flushToken(result, current, tokenFont, context);
                    result.add(new ResolvedText("\n", context.font(), context.fontSize(), context.color()));
                    whitespace = false;
                    tokenFont = context.font();
                    continue;
                }
                var font = resolveFont(codePoint, context);
                var nextWhitespace = Character.isWhitespace(codePoint);
                if (current.length() > 0 && (nextWhitespace != whitespace || font != tokenFont)) {
                    flushToken(result, current, tokenFont, context);
                }
                whitespace = nextWhitespace;
                tokenFont = font;
                current.appendCodePoint(codePoint);
            }
            flushToken(result, current, tokenFont, context);
        }
        return result;
    }

    private static void flushToken(List<ResolvedText> target, StringBuilder value, PdfFont font, TextContext context) {
        if (value.length() > 0) {
            target.add(new ResolvedText(value.toString(), font, context.fontSize(), context.color()));
            value.setLength(0);
        }
    }

    private void renderLines(ParagraphLayout layout, float x, float top, float width, TextAlignment alignment,
                             float opacity, boolean underline, boolean strikethrough) {
        var lineTop = top;
        for (int lineIndex = 0; lineIndex < layout.lines().size(); lineIndex++) {
            var line = layout.lines().get(lineIndex);
            var lastLine = lineIndex == layout.lines().size() - 1 || line.hardBreak();
            var justify = alignment == TextAlignment.JUSTIFY && !lastLine;
            var leftover = justify ? Math.max(0f, width - line.width()) : 0f;
            var gaps = justify ? Math.max(1, line.wordGaps()) : 1;
            var extra = leftover / gaps;
            var lineX = switch (alignment) {
                case LEFT, JUSTIFY -> x;
                case CENTER -> x + (width - line.width()) / 2f;
                case RIGHT -> x + width - line.width();
            };
            var fragmentX = lineX;
            for (var fragment : line.fragments()) {
                var baseline = lineTop - fragment.font().ascent(fragment.fontSize());
                PdfDrawing.text(document, page, fragment.value(), fragment.font(), fragment.fontSize(), fragment.color(),
                    fragmentX, baseline, fragment.width(), TextAlignment.LEFT, 0, opacity);
                if (underline && !fragment.value().isBlank()) {
                    PdfDrawing.line(document, page, fragment.color(), Math.max(0.6f, fragment.fontSize() / 14f),
                        fragmentX, baseline - 1.2f, fragmentX + fragment.width(), baseline - 1.2f);
                }
                if (strikethrough && !fragment.value().isBlank()) {
                    var mid = baseline + fragment.fontSize() * 0.28f;
                    PdfDrawing.line(document, page, fragment.color(), Math.max(0.6f, fragment.fontSize() / 16f),
                        fragmentX, mid, fragmentX + fragment.width(), mid);
                }
                fragmentX += fragment.width();
                if (justify && fragment.value().isBlank()) {
                    fragmentX += extra;
                }
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
                    context.textAlignment(), opacity, context.underline(), context.strikethrough());
                annotate(style, x, y, width, height);
            }
            case Div div -> {
                var style = div.style();
                var height = Math.max(floatOr(style.height(), 0), estimate(div, width, defaultText));
                drawBlockDecoration(style, x, y, width, height, opacity);
                var previousBreaks = allowPageBreaks;
                var savedTop = cursorTop;
                allowPageBreaks = false;
                cursorTop = y + height - style.paddingTop();
                var context = resolveTextContext(style, defaultText);
                var childX = x + style.paddingLeft();
                var childWidth = Math.max(1f, width - style.paddingLeft() - style.paddingRight());
                for (var child : div.children()) {
                    renderFlow(child, context, childX, childWidth);
                }
                cursorTop = savedTop;
                allowPageBreaks = previousBreaks;
            }
            case Image image -> PdfDrawing.image(document, targetPage, image.source(), x, y,
                image.getImageScaledWidth(), image.getImageScaledHeight());
            case LineSeparator separator -> PdfDrawing.line(document, targetPage, separator.line().color(),
                separator.line().lineWidth(), x, y, x + width, y);
            case ListBlock list -> {
                var savedTop = cursorTop;
                cursorTop = y + estimate(list, width, defaultText);
                renderFlowList(list, defaultText, x, width);
                cursorTop = savedTop;
            }
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
        drawBackground(style, x, y, width, height, opacity);
        PdfDrawing.borders(document, page, style.borderTop(), style.borderRight(), style.borderBottom(),
            style.borderLeft(), x, y, width, height, style.borderRadius());
    }

    private void drawBackground(Style style, float x, float y, float width, float height, float opacity) {
        if (style.backgroundGradient() != null) {
            PdfDrawing.fillGradient(document, page, style.backgroundGradient(), x, y, width, height,
                style.borderRadius(), opacity);
        } else if (style.backgroundColor() != null) {
            PdfDrawing.fillRounded(document, page, style.backgroundColor(), x, y, width, height,
                style.borderRadius(), opacity);
        }
    }

    private TextContext resolveTextContext(Style style, TextContext inherited) {
        var font = style.font() == null ? inherited.font() : style.font();
        if (PdfFontFactory.bundled(font)) {
            font = PdfFontFactory.create(style.bold() || font.bold(), style.italic() || font.italic());
        }
        return new TextContext(
            font,
            style.fontSize(inherited.fontSize()),
            style.fontColor(inherited.color()),
            style.textAlignment(inherited.textAlignment()),
            style.resolvedLeading(inherited.multipliedLeading()),
            style.underline() || inherited.underline(),
            style.strikethrough() || inherited.strikethrough(),
            inherited.fallbacks()
        );
    }

    private PdfFont resolveFont(int codePoint, TextContext context) {
        if (context.font().supports(codePoint) || Character.isWhitespace(codePoint)) {
            return context.font();
        }
        for (var fallback : context.fallbacks()) {
            if (fallback.supports(codePoint)) {
                return fallback;
            }
        }
        return context.font();
    }

    private void annotate(Style style, float x, float y, float width, float height) {
        PdfDrawing.link(page, x, y, width, height, style.destinationUri(), style.destinationPage(),
            style.namedDestination());
        if (style.localDestination() != null) {
            document.addNamedDestination(style.localDestination(), Math.max(1, document.getNumberOfPages()),
                y + height);
        }
    }

    private static List<Row> rows(List<Cell> cells, int numberOfColumns) {
        var rows = new ArrayList<Row>();
        var occupancy = new int[numberOfColumns];
        var iterator = cells.iterator();
        Cell pending = iterator.hasNext() ? iterator.next() : null;
        while (pending != null || remainingOccupancy(occupancy)) {
            var current = new ArrayList<PlacedCell>();
            var column = 0;
            while (column < numberOfColumns) {
                if (occupancy[column] > 0) {
                    column++;
                    continue;
                }
                if (pending == null) {
                    break;
                }
                if (column + pending.columnSpan() > numberOfColumns) {
                    throw new IllegalArgumentException("Cell span exceeds the table width");
                }
                for (int occupied = column; occupied < column + pending.columnSpan(); occupied++) {
                    if (occupancy[occupied] > 0) {
                        throw new IllegalArgumentException("Cell overlaps a row-spanned column");
                    }
                    occupancy[occupied] = pending.rowSpan();
                }
                current.add(new PlacedCell(pending, column));
                column += pending.columnSpan();
                pending = iterator.hasNext() ? iterator.next() : null;
            }
            if (current.isEmpty() && !remainingOccupancy(occupancy)) {
                break;
            }
            rows.add(new Row(List.copyOf(current)));
            for (int index = 0; index < occupancy.length; index++) {
                if (occupancy[index] > 0) {
                    occupancy[index]--;
                }
            }
        }
        if (pending != null) {
            throw new IllegalArgumentException("Table has leftover cells that do not fit the grid");
        }
        return List.copyOf(rows);
    }

    private static boolean remainingOccupancy(int[] occupancy) {
        for (var value : occupancy) {
            if (value > 0) {
                return true;
            }
        }
        return false;
    }

    private static float[] resolvedColumnWidths(Table table, float tableWidth) {
        var units = table.columnUnits();
        if (units == null) {
            return resolvedColumnWidths(table.columnWidths(), tableWidth);
        }
        var pointTotal = 0f;
        var percentTotal = 0f;
        for (var unit : units) {
            if (unit.unitType() == UnitValue.UnitType.POINT) {
                pointTotal += unit.value();
            } else {
                percentTotal += unit.value();
            }
        }
        var percentBudget = percentTotal <= 0 ? 0f : tableWidth * percentTotal / 100f;
        if (pointTotal + percentBudget > tableWidth && percentTotal > 0) {
            percentBudget = Math.max(0f, tableWidth - pointTotal);
        }
        var result = new float[units.length];
        for (int index = 0; index < units.length; index++) {
            var unit = units[index];
            result[index] = unit.unitType() == UnitValue.UnitType.POINT
                ? unit.value()
                : percentTotal == 0 ? 0f : percentBudget * unit.value() / percentTotal;
        }
        return result;
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
            cursorTop = pageSize.getHeight() - topInset();
        }
    }

    private void newPage() {
        if (!allowPageBreaks) {
            return;
        }
        page = document.addNewPage(pageSize);
        cursorTop = pageSize.getHeight() - topInset();
    }

    private float renderNested(LayoutElement element, TextContext inherited, float x, float top, float width) {
        var previousBreaks = allowPageBreaks;
        var savedTop = cursorTop;
        allowPageBreaks = false;
        cursorTop = top;
        renderFlow(element, inherited, x, width);
        var bottom = cursorTop;
        cursorTop = savedTop;
        allowPageBreaks = previousBreaks;
        return bottom;
    }

    private static float[] fittedImageSize(Image image, float availableWidth) {
        var width = Math.min(image.getImageScaledWidth(), Math.max(1f, availableWidth));
        var scale = width / Math.max(image.getImageScaledWidth(), 0.01f);
        return new float[] {width, image.getImageScaledHeight() * scale};
    }

    private float contentWidth() {
        return pageSize.getWidth() - leftMargin - rightMargin;
    }

    private float contentHeight() {
        return pageSize.getHeight() - topInset() - bottomInset();
    }

    private float remainingHeight() {
        return cursorTop - bottomInset();
    }

    private float topInset() {
        return topMargin + headerBand(document.getNumberOfPages());
    }

    private float headerBand(int pageNumber) {
        return pageNumber <= 1 && firstHeaderHeight > 0 ? firstHeaderHeight : headerHeight;
    }

    private float bottomInset() {
        return bottomMargin + footerHeight;
    }

    private record TextContext(PdfFont font, float fontSize, Color color, TextAlignment textAlignment,
                               float multipliedLeading, boolean underline, boolean strikethrough,
                               List<PdfFont> fallbacks) {
    }

    private record ResolvedText(String value, PdfFont font, float fontSize, Color color) {
    }

    private record TextFragment(String value, PdfFont font, float fontSize, Color color, float width) {
    }

    private record TextLine(List<TextFragment> fragments, float width, float height, boolean hardBreak) {
        int wordGaps() {
            var gaps = 0;
            for (var fragment : fragments) {
                if (fragment.value().isBlank()) {
                    gaps++;
                }
            }
            return gaps;
        }
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

        TextLine finish(TextContext context, boolean hardBreak) {
            var height = Math.max(MINIMUM_LINE_HEIGHT,
                (maximumFontSize == 0 ? context.fontSize() : maximumFontSize) * context.multipliedLeading());
            return new TextLine(List.copyOf(fragments), width, height, hardBreak);
        }
    }
}
