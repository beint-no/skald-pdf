package org.skaldpdf.colors;

public record DeviceRgb(int redValue, int greenValue, int blueValue) implements Color {
    public DeviceRgb {
        validate(redValue);
        validate(greenValue);
        validate(blueValue);
    }

    public static DeviceRgb of(int red, int green, int blue) {
        return new DeviceRgb(red, green, blue);
    }

    public static DeviceRgb of(int rgb) {
        return new DeviceRgb((rgb >>> 16) & 0xff, (rgb >>> 8) & 0xff, rgb & 0xff);
    }

    public static DeviceRgb hex(String value) {
        var text = java.util.Objects.requireNonNull(value, "value").strip();
        if (text.startsWith("#")) {
            text = text.substring(1);
        }
        if (text.length() == 3) {
            text = "" + text.charAt(0) + text.charAt(0) + text.charAt(1) + text.charAt(1)
                + text.charAt(2) + text.charAt(2);
        }
        if (text.length() != 6 || !text.chars().allMatch(code -> Character.digit(code, 16) >= 0)) {
            throw new IllegalArgumentException("RGB hex colors must be #RGB or #RRGGBB");
        }
        return of(Integer.parseInt(text, 16));
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
