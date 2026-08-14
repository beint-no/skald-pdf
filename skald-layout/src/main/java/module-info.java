module org.skaldpdf.layout {
    requires transitive org.skaldpdf.core;
    requires org.skaldpdf.fonts;

    exports org.skaldpdf;
    exports org.skaldpdf.layout;
    exports org.skaldpdf.layout.borders;
    exports org.skaldpdf.layout.canvas;
    exports org.skaldpdf.layout.element;
    exports org.skaldpdf.layout.properties;
}
