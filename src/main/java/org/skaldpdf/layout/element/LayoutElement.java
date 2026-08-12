package org.skaldpdf.layout.element;

import org.skaldpdf.layout.Style;

public sealed interface LayoutElement permits AbstractElement {
    Style style();
}
