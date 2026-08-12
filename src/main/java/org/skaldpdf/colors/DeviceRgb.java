package org.skaldpdf.colors;

public record DeviceRgb(int redValue, int greenValue, int blueValue) implements Color {
    public DeviceRgb {
        validate(redValue);
        validate(greenValue);
        validate(blueValue);
    }

    private static void validate(int value) {
        if (value < 0 || value > 255) {
            throw new IllegalArgumentException("RGB channels must be between 0 and 255");
        }
    }

    @Override
    public float red() {
        return redValue / 255f;
    }

    @Override
    public float green() {
        return greenValue / 255f;
    }

    @Override
    public float blue() {
        return blueValue / 255f;
    }
}
