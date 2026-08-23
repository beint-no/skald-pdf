import org.jspecify.annotations.NullMarked;

@NullMarked
module org.skaldpdf.labels {
    requires static transitive org.jspecify;

    requires transitive org.skaldpdf.core;
    requires org.skaldpdf.barcode;
    requires org.skaldpdf.fonts;

    exports org.skaldpdf.labels;
}
