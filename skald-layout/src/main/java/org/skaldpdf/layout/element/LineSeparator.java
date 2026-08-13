package org.skaldpdf.layout.element;

import org.skaldpdf.layout.canvas.SolidLine;

import java.util.Objects;

public final class LineSeparator extends AbstractElement<LineSeparator> {
    private final SolidLine line;

    public LineSeparator(SolidLine line) {
        this.line = Objects.requireNonNull(line, "line");
    }

    public SolidLine line() {
        return line;
    }

    @Override
    protected LineSeparator self() {
        return this;
    }
}
