import org.jspecify.annotations.NullMarked;

/** Explicitly experimental JPEG XL PDF image optimization. */
@NullMarked
module org.skaldpdf.optimize.jxl {
    requires static transitive org.jspecify;
    requires transitive org.skaldpdf.optimize;
    requires transitive org.skaldpdf.codec;

    exports org.skaldpdf.optimize.jxl;
}
