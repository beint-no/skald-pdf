package org.skaldpdf.layout.element;

import org.skaldpdf.layout.borders.Border;
import org.skaldpdf.layout.borders.SolidBorder;
import org.skaldpdf.layout.properties.UnitValue;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class Table extends AbstractElement<Table> {
    private final float[] columnWidths;
    private final UnitValue[] columnUnits;
    private final List<Cell> headerCells = new ArrayList<>();
    private final List<Cell> footerCells = new ArrayList<>();
    private final List<Cell> cells = new ArrayList<>();

    public Table(int numberOfColumns) {
        this(filledWeights(numberOfColumns), null);
    }

    public Table(float[] columnWidths) {
        this(validatedWidths(columnWidths), null);
    }

    public Table(UnitValue columnWidths) {
        this(columnWidths.values());
    }

    /** Mixed point and percent columns; percent columns share leftover width after fixed columns. */
    public static Table withColumns(UnitValue... columns) {
        Objects.requireNonNull(columns, "columns");
        if (columns.length == 0) {
            throw new IllegalArgumentException("A table needs at least one column");
        }
        var widths = new float[columns.length];
        var units = new UnitValue[columns.length];
        for (int index = 0; index < columns.length; index++) {
            var unit = Objects.requireNonNull(columns[index], "column");
            if (unit.unitType() != UnitValue.UnitType.POINT && unit.unitType() != UnitValue.UnitType.PERCENT) {
                throw new IllegalArgumentException("Mixed columns must be point or percent values");
            }
            units[index] = unit;
            widths[index] = unit.value();
        }
        return new Table(widths, units);
    }

    private Table(float[] columnWidths, UnitValue[] columnUnits) {
        this.columnWidths = columnWidths;
        this.columnUnits = columnUnits;
    }

    private static float[] filledWeights(int numberOfColumns) {
        if (numberOfColumns < 1) {
            throw new IllegalArgumentException("A table needs at least one column");
        }
        var widths = new float[numberOfColumns];
        java.util.Arrays.fill(widths, 1f);
        return widths;
    }

    private static float[] validatedWidths(float[] values) {
        Objects.requireNonNull(values, "columnWidths");
        if (values.length == 0) {
            throw new IllegalArgumentException("A table needs at least one column");
        }
        var result = values.clone();
        for (float value : result) {
            if (!(value > 0)) {
                throw new IllegalArgumentException("Column widths must be positive");
            }
        }
        return result;
    }

    public Table addHeaderCell(Cell cell) {
        headerCells.add(Objects.requireNonNull(cell, "cell"));
        return this;
    }

    public Table addHeaderCell(String content) {
        return addHeaderCell(new Cell().add(new Paragraph(content)));
    }

    public Table addFooterCell(Cell cell) {
        footerCells.add(Objects.requireNonNull(cell, "cell"));
        return this;
    }

    public Table addFooterCell(String content) {
        return addFooterCell(new Cell().add(new Paragraph(content)));
    }

    public Table addFooterRow(String... values) {
        requireCompleteRow(values);
        for (var value : values) {
            addFooterCell(value);
        }
        return this;
    }

    public Table addCell(Cell cell) {
        cells.add(Objects.requireNonNull(cell, "cell"));
        return this;
    }

    public Table addCell(String content) {
        return addCell(new Cell().add(new Paragraph(content)));
    }

    public Table addCell(LayoutElement element) {
        return addCell(new Cell().add(element));
    }

    public Table addHeaderRow(String... values) {
        requireCompleteRow(values);
        for (var value : values) {
            addHeaderCell(value);
        }
        return this;
    }

    public Table addRow(String... values) {
        requireCompleteRow(values);
        for (var value : values) {
            addCell(value);
        }
        return this;
    }

    private void requireCompleteRow(String[] values) {
        Objects.requireNonNull(values, "values");
        if (values.length != columnWidths.length) {
            throw new IllegalArgumentException(
                "Row has " + values.length + " values but the table has " + columnWidths.length + " columns");
        }
    }

    public Table useAllAvailableWidth() {
        setWidth(UnitValue.createPercentValue(100f));
        return this;
    }

    /**
     * Full-width hairline. The stroke sits at the bottom of a short spacer row so
     * it cannot paint through the previous row's baseline.
     */
    public Table addRule(float width) {
        if (!(width > 0) || !Float.isFinite(width)) {
            throw new IllegalArgumentException("Rule width must be positive and finite");
        }
        var clearance = Math.max(3.5f, width);
        return addCell(new Cell(numberOfColumns())
            .setBorder(Border.NO_BORDER)
            .setBorderBottom(new SolidBorder(width))
            .setPadding(0)
            .setHeight(width + clearance));
    }

    public int numberOfColumns() {
        return columnWidths.length;
    }

    public float[] columnWidths() {
        return columnWidths.clone();
    }

    public UnitValue[] columnUnits() {
        return columnUnits == null ? null : columnUnits.clone();
    }

    public List<Cell> headerCells() {
        return List.copyOf(headerCells);
    }

    public List<Cell> footerCells() {
        return List.copyOf(footerCells);
    }

    public List<Cell> cells() {
        return List.copyOf(cells);
    }

    @Override
    protected Table self() {
        return this;
    }
}
