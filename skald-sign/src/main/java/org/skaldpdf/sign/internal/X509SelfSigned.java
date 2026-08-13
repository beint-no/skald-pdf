package org.skaldpdf.sign.internal;

import java.io.ByteArrayInputStream;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/** Minimal self-signed X.509 v3 writer for development keys and tests. */
public final class X509SelfSigned {
    private static final DateTimeFormatter UTC = DateTimeFormatter.ofPattern("yyMMddHHmmss'Z'")
        .withZone(ZoneOffset.UTC);

    private X509SelfSigned() {
    }

    public static X509Certificate create(KeyPair keyPair, String commonName, String organization,
                                         String country, Instant notBefore, Instant notAfter) {
        Objects.requireNonNull(keyPair, "keyPair");
        Objects.requireNonNull(commonName, "commonName");
        Objects.requireNonNull(organization, "organization");
        Objects.requireNonNull(country, "country");
        if (country.length() != 2 || !country.chars().allMatch(Character::isLetter)) {
            throw new IllegalArgumentException("Country must be an ISO 3166-1 alpha-2 code");
        }
        if (!notAfter.isAfter(notBefore)) {
            throw new IllegalArgumentException("Certificate notAfter must be after notBefore");
        }
        try {
            var name = name(country.toUpperCase(), organization, commonName);
            var serial = new BigInteger(64, new SecureRandom()).abs();
            if (serial.equals(BigInteger.ZERO)) {
                serial = BigInteger.ONE;
            }
            var algorithm = Der.algorithm(Oids.SHA256_WITH_RSA);
            var extensions = Der.explicit(3, Der.sequence(
                extension(Oids.KEY_USAGE, true, Der.bitString(6, new byte[] {(byte) 0xc0})),
                extension(Oids.BASIC_CONSTRAINTS, true, Der.sequence(Der.booleanValue(false)))
            ));
            var tbs = Der.sequence(
                Der.explicit(0, Der.integer(2)),
                Der.integer(serial),
                algorithm,
                Der.raw(name),
                Der.sequence(Der.utcTime(UTC.format(notBefore)), Der.utcTime(UTC.format(notAfter))),
                Der.raw(name),
                Der.raw(keyPair.getPublic().getEncoded()),
                extensions
            );
            var signer = Signature.getInstance("SHA256withRSA");
            signer.initSign(keyPair.getPrivate());
            signer.update(tbs);
            var certificate = Der.sequence(tbs, algorithm, Der.bitString(0, signer.sign()));
            return (X509Certificate) CertificateFactory.getInstance("X.509")
                .generateCertificate(new ByteArrayInputStream(certificate));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to create a self-signed certificate", exception);
        }
    }

    private static byte[] name(String country, String organization, String commonName) {
        return Der.sequence(
            rdn(Oids.COUNTRY_NAME, Der.printableString(country)),
            rdn(Oids.ORGANIZATION_NAME, Der.utf8String(organization)),
            rdn(Oids.COMMON_NAME, Der.utf8String(commonName))
        );
    }

    private static byte[] rdn(byte[] oid, byte[] value) {
        return Der.set(Der.sequence(oid, value));
    }

    private static byte[] extension(byte[] oid, boolean critical, byte[] value) {
        return Der.sequence(oid, Der.booleanValue(critical), Der.octetString(value));
    }
}
