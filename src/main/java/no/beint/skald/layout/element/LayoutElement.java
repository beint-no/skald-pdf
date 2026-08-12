package no.beint.skald.layout.element;

import no.beint.skald.layout.Style;

public sealed interface LayoutElement permits AbstractElement {
    Style style();
}
