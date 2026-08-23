import org.jspecify.annotations.NullMarked;

@NullMarked
module org.skaldpdf.optimize {
    requires static transitive org.jspecify;

    requires transitive org.skaldpdf.core;
    requires org.skaldpdf.codec;

    exports org.skaldpdf.optimize;
}
