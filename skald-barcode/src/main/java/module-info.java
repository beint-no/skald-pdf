import org.jspecify.annotations.NullMarked;

@NullMarked
module org.skaldpdf.barcode {
    requires static transitive org.jspecify;

    requires transitive org.skaldpdf.core;
    requires org.skaldpdf.fonts;

    exports org.skaldpdf.barcode;
}
