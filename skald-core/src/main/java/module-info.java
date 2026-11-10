import org.jspecify.annotations.NullMarked;

@NullMarked
module org.skaldpdf.core {
    requires static transitive org.jspecify;

    exports org.skaldpdf.colors;
    exports org.skaldpdf.event;
    exports org.skaldpdf.font;
    exports org.skaldpdf.geom;
    exports org.skaldpdf.image;
    exports org.skaldpdf.pdf;
    exports org.skaldpdf.pdf.spi;
    uses org.skaldpdf.pdf.spi.BrotliCodec;
    uses org.skaldpdf.pdf.spi.JpegXlCodec;
    exports org.skaldpdf.pdf.canvas;
    exports org.skaldpdf.pdf.extgstate;
    exports org.skaldpdf.pdf.merge;
}
