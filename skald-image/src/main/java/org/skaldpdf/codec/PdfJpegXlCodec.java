package org.skaldpdf.codec;

import org.skaldpdf.image.ImageData;
import org.skaldpdf.pdf.spi.JpegXlCodec;

/** Service used by core to validate/decode provisional JPEG XL PDF image streams. */
public final class PdfJpegXlCodec implements JpegXlCodec {
    /** Creates the optional service. */
    public PdfJpegXlCodec() {}

    @Override
    public boolean available() {
        return NativeImages.jpegXlEncodingAvailable();
    }

    @Override
    public ImageData decode(byte[] encoded) {
        return JpegXl.decodePdf(encoded);
    }
}
