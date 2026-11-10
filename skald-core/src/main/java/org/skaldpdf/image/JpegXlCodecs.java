package org.skaldpdf.image;

import org.skaldpdf.pdf.spi.JpegXlCodec;

import java.util.ServiceLoader;

/** Access to the optional JPEG XL decoder without a core dependency on native codecs. */
public final class JpegXlCodecs {
    private JpegXlCodecs() {}

    /** Whether an optional decoder is available. */
    public static boolean available() {
        return ServiceLoader.load(JpegXlCodec.class).stream()
                .map(ServiceLoader.Provider::get)
                .anyMatch(JpegXlCodec::available);
    }

    /** Decodes a static opaque image, subject to the provider's resource limits. */
    public static ImageData decode(byte[] encoded) {
        return ServiceLoader.load(JpegXlCodec.class).stream()
                .map(ServiceLoader.Provider::get)
                .filter(JpegXlCodec::available)
                .findFirst()
                .orElseThrow(() ->
                        new IllegalStateException(
                                "JPEG XL requires skald-image, libjxl and native-access"
                                        + " permission"))
                .decode(encoded);
    }
}
