import org.jspecify.annotations.NullMarked;

/** Norwegian purchase order. */

@NullMarked
module org.skaldpdf.purchase.no {
    requires static transitive org.jspecify;

    requires transitive org.skaldpdf.invoice.no;

    exports org.skaldpdf.purchase.no;
}
