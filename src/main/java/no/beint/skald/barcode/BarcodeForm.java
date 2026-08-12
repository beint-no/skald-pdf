package no.beint.skald.barcode;

import no.beint.skald.image.ImageSource;

public record BarcodeForm(String code, byte[] modules, float moduleWidth, float barHeight, float fontSize,
                          boolean guardBars) implements ImageSource {
    public BarcodeForm {
        modules = modules.clone();
    }

    @Override
    public byte[] modules() {
        return modules.clone();
    }

    @Override
    public float intrinsicWidth() {
        return modules.length * moduleWidth;
    }

    @Override
    public float intrinsicHeight() {
        return barHeight + fontSize + 2f;
    }
}
