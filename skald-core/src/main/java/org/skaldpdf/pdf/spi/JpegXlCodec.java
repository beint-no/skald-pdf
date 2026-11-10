package org.skaldpdf.pdf.spi;

import org.skaldpdf.image.ImageData;

/** Optional decoding of static, opaque RGB8/Gray8 JPEG XL for experimental PDF images. */
public interface JpegXlCodec {
    /** Whether the native decoder is available. */
    boolean available();

    /**
     * Fully validates and decodes one static image; rejects alpha, animation and unsupported
     * precision.
     */
    ImageData decode(byte[] encoded);
}
