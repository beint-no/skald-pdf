import org.jspecify.annotations.NullMarked;

@NullMarked
module org.skaldpdf.sign {
    requires static transitive org.jspecify;

    requires transitive org.skaldpdf.core;

    exports org.skaldpdf.sign;
}
