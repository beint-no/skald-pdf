package org.skaldpdf.colors;

import java.util.Objects;

/** A two-stop axial gradient in DeviceRGB. */
public record LinearGradient(Color start, Color end, Direction direction) {
    public enum Direction {
        HORIZONTAL,
        VERTICAL
    }

    public LinearGradient {
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(end, "end");
        Objects.requireNonNull(direction, "direction");
    }

    public static LinearGradient vertical(Color start, Color end) {
        return new LinearGradient(start, end, Direction.VERTICAL);
    }

    public static LinearGradient horizontal(Color start, Color end) {
        return new LinearGradient(start, end, Direction.HORIZONTAL);
    }
}
