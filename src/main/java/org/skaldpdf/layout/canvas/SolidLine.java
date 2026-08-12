package org.skaldpdf.layout.canvas;

import org.skaldpdf.colors.Color;
import org.skaldpdf.colors.ColorConstants;

public final class SolidLine {
    private final float lineWidth;
    private final Color color;

    public SolidLine() {
        this(1f);
    }

    public SolidLine(float lineWidth) {
        this(lineWidth, ColorConstants.BLACK);
    }

    public SolidLine(float lineWidth, Color color) {
        this.lineWidth = lineWidth;
        this.color = color;
    }

    public float lineWidth() {
        return lineWidth;
    }

    public Color color() {
        return color;
    }
}
