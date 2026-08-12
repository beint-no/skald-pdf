package no.beint.skald.layout.borders;

import no.beint.skald.colors.Color;
import no.beint.skald.colors.ColorConstants;

public class Border {
    public static final Border NO_BORDER = new Border(ColorConstants.BLACK, 0, false);

    private final Color color;
    private final float width;
    private final boolean visible;

    protected Border(Color color, float width, boolean visible) {
        this.color = color;
        this.width = width;
        this.visible = visible;
    }

    public Color color() {
        return color;
    }

    public float width() {
        return width;
    }

    public boolean visible() {
        return visible && width > 0;
    }
}
