package no.beint.skald.colors;

public sealed interface Color permits DeviceRgb {
    float red();

    float green();

    float blue();
}
