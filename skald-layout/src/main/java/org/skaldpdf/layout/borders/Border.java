package org.skaldpdf.layout.borders;

import org.skaldpdf.colors.Color;
import org.skaldpdf.colors.ColorConstants;

import java.util.Objects;

public class Border {
    public static final Border NO_BORDER = new Border(ColorConstants.BLACK, 0, false);

    private final Color color;
    private final float width;
    private final boolean visible;

    protected Border(Color color, float width, boolean visible) {
        this.color = Objects.requireNonNull(color, "color");
        if (width < 0 || !Float.isFinite(width)) {
            throw new IllegalArgumentException("Border width must be non-negative and finite");
        }
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
