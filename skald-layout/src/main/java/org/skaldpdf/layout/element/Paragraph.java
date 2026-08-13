package org.skaldpdf.layout.element;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class Paragraph extends AbstractElement<Paragraph> {
    private final List<Text> textRuns = new ArrayList<>();

    public Paragraph() {
    }

    public Paragraph(String text) {
        add(text);
    }

    public Paragraph(Text text) {
        add(text);
    }

    public Paragraph add(String text) {
        textRuns.add(new Text(Objects.requireNonNull(text, "text")));
        return this;
    }

    public Paragraph add(Text text) {
        textRuns.add(Objects.requireNonNull(text, "text"));
        return this;
    }

    public List<Text> textRuns() {
        return List.copyOf(textRuns);
    }

    public String plainText() {
        var value = new StringBuilder();
        textRuns.forEach(run -> value.append(run.value()));
        return value.toString();
    }

    @Override
    protected Paragraph self() {
        return this;
    }
}
