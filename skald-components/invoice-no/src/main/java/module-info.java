import org.jspecify.annotations.NullMarked;

/** Opinionated Norwegian invoice theme and shared commercial letterhead. */

@NullMarked
module org.skaldpdf.invoice.no {
    requires static transitive org.jspecify;

    requires transitive org.skaldpdf.layout;
    requires org.skaldpdf.barcode;
    requires org.skaldpdf.fonts;

    exports org.skaldpdf.invoice.no;
}
