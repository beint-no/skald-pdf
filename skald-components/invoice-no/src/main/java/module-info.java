/** Opinionated Norwegian invoice theme and shared commercial letterhead. */
module org.skaldpdf.invoice.no {
    requires transitive org.skaldpdf.layout;
    requires org.skaldpdf.barcode;
    requires org.skaldpdf.fonts;

    exports org.skaldpdf.invoice.no;
}
