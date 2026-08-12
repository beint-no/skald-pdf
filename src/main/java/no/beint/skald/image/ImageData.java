package no.beint.skald.image;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Objects;

public final class ImageData implements ImageSource {
    private final byte[] bytes;
    private final float intrinsicWidth;
    private final float intrinsicHeight;

    ImageData(byte[] bytes) {
        this.bytes = Objects.requireNonNull(bytes, "bytes").clone();
        try {
            var image = ImageIO.read(new ByteArrayInputStream(bytes));
            if (image == null) {
                throw new IllegalArgumentException("Unsupported or invalid image data");
            }
            intrinsicWidth = image.getWidth();
            intrinsicHeight = image.getHeight();
        } catch (IOException exception) {
            throw new IllegalArgumentException("Unable to decode image data", exception);
        }
    }

    public byte[] bytes() {
        return bytes.clone();
    }

    @Override
    public float intrinsicWidth() {
        return intrinsicWidth;
    }

    @Override
    public float intrinsicHeight() {
        return intrinsicHeight;
    }
}
