package no.beint.skald.geom;

public class Rectangle {
    private final float left;
    private final float bottom;
    private final float width;
    private final float height;

    public Rectangle(float width, float height) {
        this(0, 0, width, height);
    }

    public Rectangle(float left, float bottom, float width, float height) {
        if (width < 0 || height < 0) {
            throw new IllegalArgumentException("Rectangle dimensions cannot be negative");
        }
        this.left = left;
        this.bottom = bottom;
        this.width = width;
        this.height = height;
    }

    public float getLeft() {
        return left;
    }

    public float getBottom() {
        return bottom;
    }

    public float getWidth() {
        return width;
    }

    public float getHeight() {
        return height;
    }

    public float getRight() {
        return left + width;
    }

    public float getTop() {
        return bottom + height;
    }
}
