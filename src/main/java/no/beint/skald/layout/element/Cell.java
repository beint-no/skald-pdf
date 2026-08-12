package no.beint.skald.layout.element;

import no.beint.skald.layout.borders.SolidBorder;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class Cell extends AbstractElement<Cell> {
    private final int rowSpan;
    private final int columnSpan;
    private final List<LayoutElement> children = new ArrayList<>();

    public Cell() {
        this(1, 1);
    }

    public Cell(int rowSpan, int columnSpan) {
        if (rowSpan < 1 || columnSpan < 1) {
            throw new IllegalArgumentException("Cell spans must be positive");
        }
        this.rowSpan = rowSpan;
        this.columnSpan = columnSpan;
        setPadding(2f);
        setBorder(new SolidBorder(0.5f));
    }

    public Cell add(LayoutElement child) {
        children.add(Objects.requireNonNull(child, "child"));
        return this;
    }

    public int rowSpan() {
        return rowSpan;
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
