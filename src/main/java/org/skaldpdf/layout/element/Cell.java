package org.skaldpdf.layout.element;

import org.skaldpdf.layout.borders.SolidBorder;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class Cell extends AbstractElement<Cell> {
    private final int columnSpan;
    private final List<LayoutElement> children = new ArrayList<>();

    public Cell() {
        this(1);
    }

    public Cell(int columnSpan) {
        if (columnSpan < 1) {
            throw new IllegalArgumentException("Column span must be positive");
        }
        this.columnSpan = columnSpan;
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

    public List<LayoutElement> children() {
        return List.copyOf(children);
    }

    @Override
    protected Cell self() {
        return this;
    }
}
