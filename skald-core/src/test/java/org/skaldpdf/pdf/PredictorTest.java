package org.skaldpdf.pdf;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.zip.DeflaterOutputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.skaldpdf.pdf.CosValue.*;

class PredictorTest {
    @ParameterizedTest
    @ValueSource(ints = {1, 2, 4, 8, 16})
    void undoesTiffDifferencingAtEveryPermittedSampleDepth(int bits) throws Exception {
        // Two rows, three pixels, two components. Wraparound and non-byte-aligned rows.
        var values = new int[]{0xff, 0x100, 0x101, 0xffff, 3, 0x1234, 7, 0x1256, 0xff01, 0, 9, 0xffff};
        var maximum = (1 << bits) - 1;
        var rowBytes = (6 * bits + 7) / 8;
        var expected = new byte[rowBytes * 2];
        var predicted = new byte[expected.length];
        for (int row = 0; row < 2; row++) {
            for (int sample = 0; sample < 6; sample++) {
                var value = values[row * 6 + sample] & maximum;
                var left = sample < 2 ? 0 : values[row * 6 + sample - 2] & maximum;
                put(expected, row * rowBytes * 8 + sample * bits, bits, value);
                put(predicted, row * rowBytes * 8 + sample * bits, bits, (value - left) & maximum);
            }
        }
        assertArrayEquals(expected, decode(predicted, 2, 2, bits, 3));
    }

    @Test
    void undoesPngBytePredictorsForPackedAndSixteenBitSamples() throws Exception {
        assertArrayEquals(new byte[]{(byte) 0xab, (byte) 0xcd}, decode(new byte[]{1, (byte) 0xab, 0x22}, 15, 1, 4, 4));
        assertArrayEquals(new byte[]{1, 2, 3, 4}, decode(new byte[]{1, 1, 2, 2, 2}, 15, 1, 16, 2));
        assertArrayEquals(new byte[]{10, 20, 11, 22}, decode(new byte[]{2, 10, 20, 2, 1, 2}, 15, 1, 8, 2));
    }

    @Test
    void rejectsMalformedPredictorsAndOverflows() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> decode(new byte[]{0}, 2, 3, 8, 2));
        assertThrows(IllegalArgumentException.class, () -> decode(new byte[]{5, 0}, 15, 1, 8, 1));
        assertThrows(IllegalArgumentException.class, () -> decode(new byte[0], 0, 1, 8, 1));
        assertThrows(IllegalArgumentException.class, () -> decode(new byte[0], 15, 1, 3, 1));
        assertThrows(ArithmeticException.class, () -> decode(new byte[0], 15, Integer.MAX_VALUE, 16, Integer.MAX_VALUE));
    }

    private static byte[] decode(byte[] predicted, int predictor, int colors, int bits, int columns) throws Exception {
        var parser = new NativePdfParser(Files.readAllBytes(Path.of(
            "skald-core/src/test/resources/codec/reai-invoice/reai-invoice-flate.pdf")));
        var output = new ByteArrayOutputStream();
        try (var compressed = new DeflaterOutputStream(output)) {
            compressed.write(predicted);
        }
        var parameters = new CosDictionary(Map.of("Predictor", number(predictor), "Colors", number(colors),
            "BitsPerComponent", number(bits), "Columns", number(columns)));
        return parser.decodedStream(new CosStream(new CosDictionary(Map.of(
            "Filter", new CosName("FlateDecode"), "DecodeParms", parameters)), output.toByteArray()), "test");
    }

    private static CosNumber number(int value) {
        return new CosNumber(Integer.toString(value));
    }

    private static void put(byte[] target, int offset, int bits, int value) {
        for (int bit = 0; bit < bits; bit++) {
            var position = offset + bit;
            target[position / 8] |= (byte) (((value >>> (bits - bit - 1)) & 1) << (7 - position % 8));
        }
    }
}
