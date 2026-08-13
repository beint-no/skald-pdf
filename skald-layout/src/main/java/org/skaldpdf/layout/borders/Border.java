package org.skaldpdf.layout.borders;

import org.skaldpdf.colors.Color;
import org.skaldpdf.colors.ColorConstants;

import java.util.Objects;

public class Border {
    public static final Border NO_BORDER = new Border(ColorConstants.BLACK, 0, false);

    private final Color color;
    private final float width;
    private final boolean visible;
    private final float dash;
    private final float gap;

    protected Border(Color color, float width, boolean visible) {
        this(color, width, visible, 0, 0);
    }

    protected Border(Color color, float width, boolean visible, float dash, float gap) {
        this.color = Objects.requireNonNull(color, "color");
        if (width < 0 || !Float.isFinite(width) || dash < 0 || gap < 0
            || !Float.isFinite(dash) || !Float.isFinite(gap)) {
            throw new IllegalArgumentException("Border dimensions must be non-negative and finite");
        }
        this.width = width;
        this.visible = visible;
        this.dash = dash;
        this.gap = gap;
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

    public float dash() {
        return dash;
    }

    public float gap() {
        return gap;
    }

    public boolean equalsStroke(Border other) {
        return other != null && visible() == other.visible() && width == other.width
            && dash == other.dash && gap == other.gap && color.equals(other.color);
    }
}
