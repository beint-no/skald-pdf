package org.skaldpdf.colors;

public sealed interface Color permits DeviceRgb {
    float red();

    float green();

    float blue();
}
