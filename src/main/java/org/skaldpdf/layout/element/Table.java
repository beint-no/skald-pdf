package org.skaldpdf.layout.element;

import org.skaldpdf.layout.properties.UnitValue;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class Table extends AbstractElement<Table> {
    private final float[] columnWidths;
    private final List<Cell> headerCells = new ArrayList<>();
    private final List<Cell> cells = new ArrayList<>();

    public Table(int numberOfColumns) {
        if (numberOfColumns < 1) {
            throw new IllegalArgumentException("A table needs at least one column");
        }
        this.columnWidths = new float[numberOfColumns];
        java.util.Arrays.fill(columnWidths, 1f);
    }

    public Table(float[] columnWidths) {
        this.columnWidths = validatedWidths(columnWidths);
    }

    public Table(UnitValue columnWidths) {
        this(columnWidths.values());
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

    public Table useAllAvailableWidth() {
        setWidth(UnitValue.createPercentValue(100f));
        return this;
    }

    public int numberOfColumns() {
        return columnWidths.length;
    }

    public float[] columnWidths() {
        return columnWidths.clone();
    }

    public List<Cell> headerCells() {
        return List.copyOf(headerCells);
    }

    public List<Cell> cells() {
        return List.copyOf(cells);
    }

    @Override
    protected Table self() {
        return this;
    }
}
