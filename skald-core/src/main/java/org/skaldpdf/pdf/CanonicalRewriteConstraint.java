package org.skaldpdf.pdf;

/** Document properties that a byte-preserving canonical rewrite must retain. */
public enum CanonicalRewriteConstraint {
    /** Byte offsets and hint tables support progressive web loading. */
    LINEARIZATION,
    /** Earlier revisions may carry audit or recovery value. */
    INCREMENTAL_HISTORY,
    /** A declared PDF/A, PDF/X, PDF/UA, PDF/E, or PDF/VT profile needs profile validation. */
    CONFORMANCE_PROFILE,
    /** Rewriting any signed byte range would invalidate the document signature. */
    DIGITAL_SIGNATURE
}
