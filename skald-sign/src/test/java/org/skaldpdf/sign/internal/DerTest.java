package org.skaldpdf.sign.internal;

import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class DerTest {
    @Test
    void encodesKnownObjectIdentifiers() {
        assertEquals("06092a864886f70d010702", HexFormat.of().formatHex(Oids.SIGNED_DATA));
        assertEquals("0609608648016503040201", HexFormat.of().formatHex(Oids.SHA_256));
        assertEquals("0603550403", HexFormat.of().formatHex(Oids.COMMON_NAME));
    }

    @Test
    void sortsSetMembers() {
        var late = Der.integer(5);
        var early = Der.integer(1);
        var set = Der.set(late, early);
        var reader = DerReader.of(set).enter(0x31);
        assertEquals(BigInteger.ONE, reader.integer());
        assertEquals(BigInteger.valueOf(5), reader.integer());
    }

    @Test
    void reconstructsImplicitAttributesAsASet() {
        var first = Der.sequence(Oids.CONTENT_TYPE, Der.set(Oids.DATA));
        var second = Der.sequence(Oids.MESSAGE_DIGEST, Der.set(Der.octetString(new byte[] {1, 2, 3})));
        var implicit = Der.implicitSet(0, first, second);
        var asSet = Der.set(first, second);
        var reconstructed = DerReader.of(implicit).enter(0xa0).contentAsSet();
        assertArrayEquals(asSet, reconstructed);
    }
}
