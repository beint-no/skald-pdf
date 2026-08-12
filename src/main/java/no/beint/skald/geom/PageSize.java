package no.beint.skald.geom;

public final class PageSize extends Rectangle {
    public static final PageSize A4 = new PageSize(595.276f, 841.89f);
    public static final PageSize A3 = new PageSize(841.89f, 1190.551f);
    public static final PageSize LETTER = new PageSize(612f, 792f);

    public PageSize(float width, float height) {
        super(width, height);
    }

    public PageSize rotate() {
        return new PageSize(getHeight(), getWidth());
    }
}
