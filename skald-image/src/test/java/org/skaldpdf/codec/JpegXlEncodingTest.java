package org.skaldpdf.codec;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.skaldpdf.image.ImageData;
import org.skaldpdf.image.JpegXlCodecs;

class JpegXlEncodingTest {
    @BeforeAll
    static void codec() {
        if ("1".equals(System.getenv("SKALD_REQUIRE_JXL_TESTS")))
            assertTrue(NativeImages.jpegXlEncodingAvailable());
        assumeTrue(NativeImages.jpegXlEncodingAvailable());
    }

    @Test
    void preservesExactRgbAndGraySamples() {
        for (var input :
                new ImageData[] {
                    ImageData.fromRgb(2, 2, new byte[] {-1, 0, 0, 0, -1, 0, 0, 0, -1, -1, -1, -1}),
                    ImageData.fromGray(2, 2, new byte[] {0, 32, -128, -1})
                }) {
            var encoded = NativeImages.jpegXlEncode(input, JpegXlOptions.lossless());
            assertTrue(encoded.jpegXl());
            var decoded = JpegXlCodecs.decode(encoded.samples());
            assertEquals(input.components(), decoded.components());
            assertArrayEquals(input.samples(), decoded.samples());
        }
    }

    @Test
    void encodesLossyGrayWithoutConvertingToRgb() {
        var input =
                ImageData.fromGray(
                        4,
                        4,
                        new byte[] {
                            0, 16, 32, 48, 64, 80, 96, 112, -128, -112, -96, -80, -64, -48, -32, -1
                        });
        var encoded = NativeImages.jpegXlEncode(input, JpegXlOptions.photos());
        assertEquals(1, JpegXlCodecs.decode(encoded.samples()).components());
    }

    @Test
    void validatesQualityAndEffort() {
        assertThrows(IllegalArgumentException.class, () -> new JpegXlOptions(Float.NaN, 7));
        assertThrows(IllegalArgumentException.class, () -> new JpegXlOptions(-1, 7));
        assertThrows(IllegalArgumentException.class, () -> new JpegXlOptions(1, 10));
    }
}
