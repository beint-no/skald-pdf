/** 100×150 mm shipping label print stock. */
module org.skaldpdf.labels.shipping {
    requires transitive org.skaldpdf.layout;
    requires org.skaldpdf.barcode;

    exports org.skaldpdf.labels.shipping;
}
