package org.skaldpdf.layout.element;

import org.skaldpdf.image.ImageSource;

import java.util.Objects;

public final class Image extends AbstractElement<Image> {
    private final ImageSource source;
    private float imageScaledWidth;
    private float imageScaledHeight;

    public Image(ImageSource source) {
        this.source = Objects.requireNonNull(source, "source");
        imageScaledWidth = source.intrinsicWidth();
        imageScaledHeight = source.intrinsicHeight();
    }

    public Image scale(float width, float height) {
        if (!(width > 0) || !(height > 0) || !Float.isFinite(width) || !Float.isFinite(height)) {
            throw new IllegalArgumentException("Image dimensions must be positive and finite");
        }
        imageScaledWidth = width;
        imageScaledHeight = height;
        return this;
    }

    public Image scaleToFit(float maximumWidth, float maximumHeight) {
        if (!(maximumWidth > 0) || !(maximumHeight > 0)
            || !Float.isFinite(maximumWidth) || !Float.isFinite(maximumHeight)) {
            throw new IllegalArgumentException("Image bounds must be positive and finite");
        }
        var scale = Math.min(1f, Math.min(maximumWidth / source.intrinsicWidth(), maximumHeight / source.intrinsicHeight()));
        imageScaledWidth = source.intrinsicWidth() * scale;
        imageScaledHeight = source.intrinsicHeight() * scale;
        return this;
    }

    public Image setFixedPosition(float x, float y) {
        style().fixedPosition(new org.skaldpdf.layout.Style.FixedPosition(0, x, y, imageScaledWidth));
        return this;
    }

    public ImageSource source() {
        return source;
    }

    public float getImageScaledWidth() {
        return imageScaledWidth;
    }

    public float getImageScaledHeight() {
        return imageScaledHeight;
    }

    @Override
    protected Image self() {
        return this;
    }
}
