package org.skaldpdf.layout.element;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class Div extends AbstractElement<Div> {
    private final List<LayoutElement> children = new ArrayList<>();

    public Div() {
    }

    public Div add(LayoutElement child) {
        children.add(Objects.requireNonNull(child, "child"));
        return this;
    }

    public List<LayoutElement> children() {
        return List.copyOf(children);
    }

    @Override
    protected Div self() {
        return this;
    }
}
