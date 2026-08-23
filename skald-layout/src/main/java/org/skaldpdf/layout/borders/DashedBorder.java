package org.skaldpdf.layout.borders;

import org.skaldpdf.colors.Color;
import org.skaldpdf.colors.ColorConstants;

public final class DashedBorder extends Border {
    public DashedBorder(float width) {
        this(ColorConstants.BLACK, width);
    }

    public DashedBorder(Color color, float width) {
        this(color, width, Math.max(1.2f, width * 3f), Math.max(0.8f, width * 2f));
    }

    public DashedBorder(Color color, float width, float dash, float gap) {
        if (!(dash > 0) || !(gap > 0)) {
            throw new IllegalArgumentException("Dash and gap must be positive");
        }
        super(color, width, true, dash, gap);
    }
}
