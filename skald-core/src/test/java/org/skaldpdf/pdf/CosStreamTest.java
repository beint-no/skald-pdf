package org.skaldpdf.pdf;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.security.MessageDigest;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class CosStreamTest {
    @Test
    void internalSinksPreserveTheDefensivelyOwnedPayload() throws Exception {
        var input = new byte[] {0, 1, 2, (byte) 255};
        var expected = input.clone();
        var stream = new CosValue.CosStream(new CosValue.CosDictionary(Map.of()), input);
        input[0] = 99;
        stream.encodedBytes()[1] = 99;
        assertEquals(expected.length, stream.encodedLength());
        var output = new ByteArrayOutputStream();
        stream.writeEncodedTo(output);
        assertArrayEquals(expected, output.toByteArray());
        var digest = MessageDigest.getInstance("SHA-256");
        stream.updateEncodedDigest(digest);
        assertArrayEquals(MessageDigest.getInstance("SHA-256").digest(expected), digest.digest());
        assertArrayEquals(expected, stream.encodedBytes());
    }
}
