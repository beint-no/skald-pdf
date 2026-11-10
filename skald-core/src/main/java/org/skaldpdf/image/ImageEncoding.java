package org.skaldpdf.image;

/** Representation of prepared image samples. */
public enum ImageEncoding {
    RAW,
    JPEG,
    /** Provisional /JXLDecode integration; not a finalized PDF standard. */
    EXPERIMENTAL_JPEG_XL
}
