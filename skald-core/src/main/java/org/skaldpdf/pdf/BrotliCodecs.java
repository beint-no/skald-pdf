package org.skaldpdf.pdf;

import org.skaldpdf.pdf.spi.BrotliCodec;

import java.util.Optional;
import java.util.ServiceLoader;

/** Keeps the optional codec outside the core runtime dependency graph. */
final class BrotliCodecs {
    private BrotliCodecs() {
    }

    private static final class Providers {
        private static final Optional<BrotliCodec> DECODER = ServiceLoader.load(BrotliCodec.class).stream()
            .map(ServiceLoader.Provider::get).filter(BrotliCodec::canDecode).findFirst();
        private static final Optional<BrotliCodec> ENCODER = ServiceLoader.load(BrotliCodec.class).stream()
            .map(ServiceLoader.Provider::get).filter(BrotliCodec::canEncode).findFirst();
    }

    static boolean canDecode() {
        return Providers.DECODER.isPresent();
    }

    static boolean canEncode() {
        return Providers.ENCODER.isPresent();
    }

    static byte[] decode(byte[] encoded, int maximumBytes) {
        return Providers.DECODER.orElseThrow(BrotliCodecs::unavailable).decode(encoded, maximumBytes);
    }

    static void validate(byte[] encoded, int maximumBytes) {
        Providers.DECODER.orElseThrow(BrotliCodecs::unavailable).validate(encoded, maximumBytes);
    }

    static byte[] encode(byte[] decoded, int quality) {
        return Providers.ENCODER.orElseThrow(BrotliCodecs::unavailable).encode(decoded, quality);
    }

    private static IllegalStateException unavailable() {
        return new IllegalStateException("Brotli requires skald-brotli, libbrotli and native-access permission; "
            + "see docs/brotli.md");
    }
}
