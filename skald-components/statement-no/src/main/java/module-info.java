import org.jspecify.annotations.NullMarked;

/** Norwegian statement of account. */

@NullMarked
module org.skaldpdf.statement.no {
    requires static transitive org.jspecify;

    requires transitive org.skaldpdf.invoice.no;

    exports org.skaldpdf.statement.no;
}
