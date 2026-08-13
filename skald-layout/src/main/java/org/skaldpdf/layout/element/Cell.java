package org.skaldpdf.layout.element;

import org.skaldpdf.layout.borders.SolidBorder;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class Cell extends AbstractElement<Cell> {
    private final int columnSpan;
    private final int rowSpan;
    private final List<LayoutElement> children = new ArrayList<>();

    public Cell() {
        this(1, 1);
    }

    public Cell(int columnSpan) {
        this(columnSpan, 1);
    }

    public Cell(int columnSpan, int rowSpan) {
        if (columnSpan < 1 || rowSpan < 1) {
            throw new IllegalArgumentException("Cell spans must be positive");
        }
        this.columnSpan = columnSpan;
        this.rowSpan = rowSpan;
        setPadding(2f);
        setBorder(new SolidBorder(0.5f));
    }

    public Cell add(LayoutElement child) {
        children.add(Objects.requireNonNull(child, "child"));
        return this;
    }

    public int columnSpan() {
        return columnSpan;
    }

    public int rowSpan() {
        return rowSpan;
    }

    public List<LayoutElement> children() {
        return List.copyOf(children);
    }

    @Override
    protected Cell self() {
        return this;
    }
}
