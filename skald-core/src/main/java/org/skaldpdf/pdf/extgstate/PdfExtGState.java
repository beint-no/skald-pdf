package org.skaldpdf.pdf.extgstate;

public final class PdfExtGState {
    private float fillOpacity = 1f;

    public PdfExtGState() {
    }

    public PdfExtGState setFillOpacity(float value) {
        if (value < 0 || value > 1) {
            throw new IllegalArgumentException("Opacity must be between 0 and 1");
        }
        fillOpacity = value;
        return this;
    }

    public float fillOpacity() {
        return fillOpacity;
    }
}
