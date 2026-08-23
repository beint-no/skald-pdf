import org.jspecify.annotations.NullMarked;

/** Norwegian A5 sales receipt. */

@NullMarked
module org.skaldpdf.receipt.no {
    requires static transitive org.jspecify;

    requires transitive org.skaldpdf.invoice.no;

    exports org.skaldpdf.receipt.no;
}
