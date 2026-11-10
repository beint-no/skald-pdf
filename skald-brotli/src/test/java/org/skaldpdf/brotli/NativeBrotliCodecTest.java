package org.skaldpdf.brotli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.skaldpdf.pdf.Compression;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class NativeBrotliCodecTest {
    private final NativeBrotliCodec codec = new NativeBrotliCodec();

    @BeforeAll
    static void requireNativeCodecInCi() {
        var available = Compression.brotliDecodingAvailable() && Compression.BROTLI_BALANCED.available();
        if ("1".equals(System.getenv("SKALD_REQUIRE_BROTLI_TESTS"))) {
            assertTrue(available, "CI must run the Brotli tests with the optional service and native libraries");
        }
        assumeTrue(available, "libbrotli is optional locally");
    }

    @ParameterizedTest
    @ValueSource(strings = {"standard", "large-window"})
    void decodesIndependentReferenceStreamsWithExactBounds(String name) throws Exception {
        var expected = "Skald PDF Brotli stream regression.\n".repeat(10000).getBytes(StandardCharsets.US_ASCII);
        byte[] encoded;
        try (var input = Objects.requireNonNull(getClass().getResourceAsStream("/brotli/" + name + ".br"))) {
            encoded = input.readAllBytes();
        }
        assertArrayEquals(expected, codec.decode(encoded, expected.length));
        codec.validate(encoded, expected.length);
        assertThrows(IllegalArgumentException.class, () -> codec.decode(encoded, expected.length - 1));
        assertThrows(IllegalArgumentException.class, () -> codec.validate(encoded, expected.length - 1));
        assertThrows(IllegalArgumentException.class, () -> codec.decode(Arrays.copyOf(encoded, encoded.length - 1), expected.length));
        assertThrows(IllegalArgumentException.class, () -> codec.decode(Arrays.copyOf(encoded, encoded.length + 1), expected.length));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 6, 11})
    void handlesEmptyAndBinaryStreams(int quality) {
        for (var input : new byte[][]{new byte[0], new byte[]{0}, new byte[]{-1, 0, 1, 42, -128}}) {
            assertArrayEquals(input, codec.decode(codec.encode(input, quality), input.length));
        }
    }

    @Test
    void supportsConcurrentDecodingWithoutSharingDecoderState() throws Exception {
        var expected = "concurrent PDF stream".repeat(20000).getBytes(StandardCharsets.US_ASCII);
        var encoded = codec.encode(expected, 6);
        try (var workers = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            var results = new java.util.ArrayList<java.util.concurrent.Future<byte[]>>();
            for (int i = 0; i < 8; i++) {
                results.add(workers.submit(() -> codec.decode(encoded, expected.length)));
            }
            for (var result : results) {
                assertArrayEquals(expected, result.get());
            }
        }
    }

    @Test
    void rejectsMalformedInputAndInvalidArguments() {
        for (var malformed : new byte[][]{new byte[0], new byte[]{-1}, new byte[]{0, 0, 0}}) {
            assertThrows(IllegalArgumentException.class, () -> codec.decode(malformed, 1024));
        }
        assertThrows(IllegalArgumentException.class, () -> codec.decode(new byte[]{59}, -1));
        assertThrows(IllegalArgumentException.class, () -> codec.encode(new byte[0], 12));
        var empty = codec.encode(new byte[0], 6);
        var concatenated = new byte[empty.length * 2];
        System.arraycopy(empty, 0, concatenated, 0, empty.length);
        System.arraycopy(empty, 0, concatenated, empty.length, empty.length);
        assertThrows(IllegalArgumentException.class, () -> codec.decode(concatenated, 1024));
    }
}
