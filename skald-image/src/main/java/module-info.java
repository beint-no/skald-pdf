import org.jspecify.annotations.NullMarked;

@NullMarked
module org.skaldpdf.codec {
    requires static transitive org.jspecify;

    requires transitive org.skaldpdf.core;
    requires java.desktop;

    exports org.skaldpdf.codec;
}
