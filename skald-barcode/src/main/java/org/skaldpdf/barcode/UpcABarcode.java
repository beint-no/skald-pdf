package org.skaldpdf.barcode;

import org.skaldpdf.image.ImageSource;
import org.skaldpdf.pdf.PdfDocument;
import org.skaldpdf.pdf.PdfPage;

import java.util.Objects;

/** UPC-A encoded as EAN-13 with a leading number-system zero. */
public final class UpcABarcode implements ImageSource {
    private final Ean13Barcode ean13;

    public UpcABarcode(String value) {
        this.ean13 = new Ean13Barcode(normalize(value));
    }

    private UpcABarcode(Ean13Barcode ean13) {
        this.ean13 = ean13;
    }

    public UpcABarcode withModuleWidth(float value) {
        return new UpcABarcode(ean13.withModuleWidth(value));
    }

    public UpcABarcode withBarHeight(float value) {
        return new UpcABarcode(ean13.withBarHeight(value));
    }

    public UpcABarcode withFontSize(float value) {
        return new UpcABarcode(ean13.withFontSize(value));
    }

    public String value() {
        return ean13.value().substring(1);
    }

    public String ean13Value() {
        return ean13.value();
    }

    public byte[] encodedModules() {
        return ean13.encodedModules();
    }

    @Override
    public float intrinsicWidth() {
        return ean13.intrinsicWidth();
    }

    @Override
    public float intrinsicHeight() {
        return ean13.intrinsicHeight();
    }

    @Override
    public void drawOn(PdfDocument document, PdfPage page, float x, float y, float width, float height) {
        ean13.drawOn(document, page, x, y, width, height);
    }

    private static String normalize(String value) {
        Objects.requireNonNull(value, "value");
        if (!value.matches("\\d{11,12}")) {
            throw new IllegalArgumentException("UPC-A requires 11 payload digits or 12 digits with a check digit");
        }
        return "0" + value;
    }
}
