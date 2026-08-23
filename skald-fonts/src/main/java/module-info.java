import org.jspecify.annotations.NullMarked;

/** Bundled, embeddable Skald Sans faces. */

@NullMarked
module org.skaldpdf.fonts {
    requires static transitive org.jspecify;

    requires transitive org.skaldpdf.core;

    exports org.skaldpdf.fonts;
}
