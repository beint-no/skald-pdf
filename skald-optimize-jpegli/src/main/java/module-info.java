import org.jspecify.annotations.NullMarked;

@NullMarked
module org.skaldpdf.optimize.jpegli {
    requires static transitive org.jspecify;

    requires transitive org.skaldpdf.optimize;
    requires no.beint.glimt;
    requires no.beint.glimt.jpegli;

    uses no.beint.glimt.spi.JpegEncoder;
    uses no.beint.glimt.spi.ImageResizer;

    exports org.skaldpdf.optimize.jpegli;
}
