import org.jspecify.annotations.NullMarked;

/** Optional native Brotli compression for PDF 2.0 streams. */
@NullMarked
module org.skaldpdf.brotli {
    requires org.skaldpdf.core;
    requires static org.jspecify;

    provides org.skaldpdf.pdf.spi.BrotliCodec with org.skaldpdf.brotli.NativeBrotliCodec;
}
