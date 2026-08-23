package org.skaldpdf.geom;

public final class PageSize extends Rectangle {
    public static final PageSize A0 = new PageSize(2383.937f, 3370.394f);
    public static final PageSize A1 = new PageSize(1683.78f, 2383.937f);
    public static final PageSize A2 = new PageSize(1190.551f, 1683.78f);
    public static final PageSize A3 = new PageSize(841.89f, 1190.551f);
    public static final PageSize A4 = new PageSize(595.276f, 841.89f);
    public static final PageSize A5 = new PageSize(419.528f, 595.276f);
    public static final PageSize A6 = new PageSize(297.638f, 419.528f);
    public static final PageSize LETTER = new PageSize(612f, 792f);
    public static final PageSize LEGAL = new PageSize(612f, 1008f);
    public static final PageSize TABLOID = new PageSize(792f, 1224f);
    public static final PageSize EXECUTIVE = new PageSize(522f, 756f);
    public static final PageSize RECEIPT_80MM = new PageSize(226.77f, 841.89f);

    public PageSize(float width, float height) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Page dimensions must be positive");
        }
        super(width, height);
    }

    public static PageSize ofMillimetres(float width, float height) {
        return new PageSize(width * 72f / 25.4f, height * 72f / 25.4f);
    }

    public static PageSize ofInches(float width, float height) {
        return new PageSize(width * 72f, height * 72f);
    }

    public PageSize rotate() {
        return new PageSize(getHeight(), getWidth());
    }

    public PageSize landscape() {
        return getWidth() >= getHeight() ? this : rotate();
    }

    public PageSize portrait() {
        return getHeight() >= getWidth() ? this : rotate();
    }
}
