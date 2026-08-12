package no.beint.skald.layout.element;

import java.util.Objects;

public final class Text extends AbstractElement<Text> {
    private final String value;

    public Text(String value) {
        this.value = Objects.requireNonNull(value, "value");
    }

    public String value() {
        return value;
    }

    @Override
    protected Text self() {
        return this;
    }
}
