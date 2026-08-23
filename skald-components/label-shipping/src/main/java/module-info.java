import org.jspecify.annotations.NullMarked;

/** 100×150 mm shipping label print stock. */

@NullMarked
module org.skaldpdf.labels.shipping {
    requires static transitive org.jspecify;

    requires transitive org.skaldpdf.layout;
    requires org.skaldpdf.barcode;

    exports org.skaldpdf.labels.shipping;
}
