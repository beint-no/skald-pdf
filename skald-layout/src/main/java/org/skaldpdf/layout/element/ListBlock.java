package org.skaldpdf.layout.element;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** An ordered or unordered list that paginates with its surrounding flow. */
public final class ListBlock extends AbstractElement<ListBlock> {
    public enum Marker {
        DISC,
        DASH,
        DECIMAL
    }

    private final Marker marker;
    private int startAt = 1;
    private final List<LayoutElement> items = new ArrayList<>();

    public ListBlock() {
        this(Marker.DISC);
    }

    public ListBlock(Marker marker) {
        this.marker = Objects.requireNonNull(marker, "marker");
        setMarginBottom(6f);
    }

    public ListBlock add(String text) {
        return add(new Paragraph(Objects.requireNonNull(text, "text")));
    }

    public ListBlock add(LayoutElement content) {
        items.add(Objects.requireNonNull(content, "content"));
        return this;
    }

    public ListBlock startAt(int value) {
        if (value < 1) {
            throw new IllegalArgumentException("List numbering must start at 1 or higher");
        }
        startAt = value;
        return this;
    }

    public int startAt() {
        return startAt;
    }

    public Marker marker() {
        return marker;
    }

    public List<LayoutElement> items() {
        return List.copyOf(items);
    }

    public float markerColumnWidth() {
        return marker == Marker.DECIMAL ? 22f : 16f;
    }

    @Override
    protected ListBlock self() {
        return this;
    }
}
