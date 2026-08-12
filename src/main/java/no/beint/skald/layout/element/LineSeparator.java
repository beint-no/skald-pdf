package no.beint.skald.layout.element;

import no.beint.skald.layout.canvas.SolidLine;

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
