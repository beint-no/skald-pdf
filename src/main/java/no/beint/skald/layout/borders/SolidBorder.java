package no.beint.skald.layout.borders;

import no.beint.skald.colors.Color;
import no.beint.skald.colors.ColorConstants;

public final class SolidBorder extends Border {
    public SolidBorder(float width) {
        this(ColorConstants.BLACK, width);
    }

    public SolidBorder(Color color, float width) {
        super(color, width, true);
    }
}
