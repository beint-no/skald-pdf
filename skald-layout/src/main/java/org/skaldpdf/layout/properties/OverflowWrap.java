package org.skaldpdf.layout.properties;

public enum OverflowWrap {
    /** CSS {@code overflow-wrap: normal}. Words are not split; a long token may overflow. */
    NORMAL,
    /** CSS {@code overflow-wrap: anywhere}. Split at any code point when a token is too wide. */
    ANYWHERE
}
