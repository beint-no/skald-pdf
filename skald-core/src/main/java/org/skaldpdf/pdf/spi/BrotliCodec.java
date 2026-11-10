package org.skaldpdf.pdf.spi;

/**
 * Optional Brotli service used by PDF stream processing. Implementations must
 * enforce the decoded byte limit and reject malformed, truncated and trailing
 * input. PDF requires raw RFC 7932 and large-window RFC
 * 9841 streams, without shared dictionaries or framing.
 */
public interface BrotliCodec {
    /** Whether this provider can decode in the current environment. */
    boolean canDecode();

    /** Whether this provider can encode in the current environment. */
    boolean canEncode();

    /** Decodes one complete stream, allowing at most {@code maximumBytes} bytes. */
    byte[] decode(byte[] encoded, int maximumBytes);

    /** Validates a stream with the same bounds as decoding, discarding its output. */
    default void validate(byte[] encoded, int maximumBytes) {
        decode(encoded, maximumBytes);
    }

    /** Encodes a raw Brotli stream without shared dictionaries at quality 0 through 11. */
    byte[] encode(byte[] decoded, int quality);
}
