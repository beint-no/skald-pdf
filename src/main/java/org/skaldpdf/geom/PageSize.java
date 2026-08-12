package org.skaldpdf.geom;

public final class PageSize extends Rectangle {
    public static final PageSize A4 = new PageSize(595.276f, 841.89f);
    public static final PageSize A3 = new PageSize(841.89f, 1190.551f);
    public static final PageSize LETTER = new PageSize(612f, 792f);

    public PageSize(float width, float height) {
        super(width, height);
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Page dimensions must be positive");
        }
    }

    public PageSize rotate() {
        return new PageSize(getHeight(), getWidth());
    }
}
