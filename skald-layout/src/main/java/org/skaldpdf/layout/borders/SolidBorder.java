package org.skaldpdf.layout.borders;

import org.skaldpdf.colors.Color;
import org.skaldpdf.colors.ColorConstants;

public final class SolidBorder extends Border {
    public SolidBorder(float width) {
        this(ColorConstants.BLACK, width);
    }

    public SolidBorder(Color color, float width) {
        super(color, width, true);
    }
}
