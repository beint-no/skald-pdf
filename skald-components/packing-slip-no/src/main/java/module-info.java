/** Norwegian packing slip and delivery note. */
module org.skaldpdf.packing.no {
    requires transitive org.skaldpdf.invoice.no;
    requires org.skaldpdf.barcode;

    exports org.skaldpdf.packing.no;
}
