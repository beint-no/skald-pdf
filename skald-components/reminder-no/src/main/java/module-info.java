import org.jspecify.annotations.NullMarked;

/** Norwegian payment reminder and collection notice. */

@NullMarked
module org.skaldpdf.reminder.no {
    requires static transitive org.jspecify;

    requires transitive org.skaldpdf.invoice.no;

    exports org.skaldpdf.reminder.no;
}
