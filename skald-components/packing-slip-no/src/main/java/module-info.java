import org.jspecify.annotations.NullMarked;

/** Norwegian packing slip and delivery note. */

@NullMarked
module org.skaldpdf.packing.no {
    requires static transitive org.jspecify;

    requires transitive org.skaldpdf.invoice.no;
    requires org.skaldpdf.barcode;

    exports org.skaldpdf.packing.no;
}
