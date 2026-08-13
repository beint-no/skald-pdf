package org.skaldpdf.sign.internal;

import org.skaldpdf.sign.SigningKey;

import java.security.GeneralSecurityException;
import java.security.Signature;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/** Detached CMS SignedData (RFC 5652) with PAdES-B-B signed attributes. */
public final class CmsSignedData {
    private static final DateTimeFormatter UTC = DateTimeFormatter.ofPattern("yyMMddHHmmss'Z'")
        .withZone(ZoneOffset.UTC);

    private CmsSignedData() {
    }

    public static byte[] detached(byte[] messageDigest, SigningKey key, Instant signingTime) {
        Objects.requireNonNull(messageDigest, "messageDigest");
        Objects.requireNonNull(key, "key");
        if (messageDigest.length != 32) {
            throw new IllegalArgumentException("PAdES-B-B uses SHA-256 message digests");
        }
        try {
            var digestAlgorithm = Der.algorithm(Oids.SHA_256);
            var contentType = attribute(Oids.CONTENT_TYPE, Oids.DATA);
            var digest = attribute(Oids.MESSAGE_DIGEST, Der.octetString(messageDigest));
            var signingCertificate = attribute(Oids.SIGNING_CERTIFICATE_V2, signingCertificateV2(key.certificate()));
            byte[] signedAttrsDer;
            byte[] signedAttrsImplicit;
            if (signingTime != null) {
                var time = attribute(Oids.SIGNING_TIME, Der.utcTime(UTC.format(signingTime)));
                signedAttrsDer = Der.set(contentType, digest, time, signingCertificate);
                signedAttrsImplicit = Der.implicitSet(0, contentType, digest, time, signingCertificate);
            } else {
                signedAttrsDer = Der.set(contentType, digest, signingCertificate);
                signedAttrsImplicit = Der.implicitSet(0, contentType, digest, signingCertificate);
            }
            var signature = sign(key, signedAttrsDer);
            var signerInfo = Der.sequence(
                Der.integer(1),
                issuerAndSerial(key.certificate()),
                digestAlgorithm,
                signedAttrsImplicit,
                Der.algorithm(key.signatureEncryptionOid()),
                Der.octetString(signature)
            );
            var signedData = Der.sequence(
                Der.integer(1),
                Der.set(digestAlgorithm),
                Der.sequence(Oids.DATA),
                Der.implicitSet(0, Der.raw(key.certificate().getEncoded())),
                Der.set(signerInfo)
            );
            return Der.sequence(Oids.SIGNED_DATA, Der.explicit(0, signedData));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Unable to build CMS SignedData", exception);
        }
    }

    private static byte[] attribute(byte[] oid, byte[] value) {
        return Der.sequence(oid, Der.set(value));
    }

    private static byte[] signingCertificateV2(X509Certificate certificate) throws CertificateEncodingException {
        return Der.sequence(
            Der.sequence(
                Der.sequence(
                    Der.algorithm(Oids.SHA_256),
                    Der.octetString(Digests.sha256(certificate.getEncoded()))
                )
            )
        );
    }

    private static byte[] issuerAndSerial(X509Certificate certificate) {
        return Der.sequence(
            Der.raw(certificate.getIssuerX500Principal().getEncoded()),
            Der.integer(certificate.getSerialNumber())
        );
    }

    private static byte[] sign(SigningKey key, byte[] data) throws GeneralSecurityException {
        var signature = Signature.getInstance(key.signatureAlgorithm());
        signature.initSign(key.privateKey());
        signature.update(data);
        return signature.sign();
    }
}
