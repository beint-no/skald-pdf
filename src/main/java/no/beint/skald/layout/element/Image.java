package no.beint.skald.layout.element;

import no.beint.skald.image.ImageSource;

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

    public Image scaleToFit(float maximumWidth, float maximumHeight) {
        var scale = Math.min(1f, Math.min(maximumWidth / source.intrinsicWidth(), maximumHeight / source.intrinsicHeight()));
        imageScaledWidth = source.intrinsicWidth() * scale;
        imageScaledHeight = source.intrinsicHeight() * scale;
        return this;
    }

    public Image setFixedPosition(float x, float y) {
        style().fixedPosition(new no.beint.skald.layout.Style.FixedPosition(0, x, y, imageScaledWidth));
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
