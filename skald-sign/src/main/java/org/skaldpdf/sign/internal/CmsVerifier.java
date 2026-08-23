package org.skaldpdf.sign.internal;

import org.skaldpdf.sign.SignatureVerification;

import java.io.ByteArrayInputStream;
import java.security.GeneralSecurityException;
import java.security.Signature;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;

/** Verifies the CMS SignedData subset Skald emits. */
public final class CmsVerifier {
    private CmsVerifier() {
    }

    public static SignatureVerification verify(byte[] cms, byte[] expectedDigest, String fieldName,
                                               String reason, String location, String contact,
                                               String pdfDate, String subFilter) {
        var notes = new ArrayList<String>();
        try {
            var contentInfo = DerReader.of(trimPadding(cms));
            var sequence = contentInfo.enter(0x30);
            if (!sequence.isOid(Oids.SIGNED_DATA)) {
                return failure(fieldName, reason, location, contact, pdfDate, subFilter,
                    "ContentInfo is not signedData", notes);
            }
            var signedData = sequence.enter(0xa0).enter(0x30);
            signedData.enter(); // version
            signedData.enter(); // digestAlgorithms
            signedData.enter(); // encapContentInfo
            if (signedData.peekTag() != 0xa0) {
                return failure(fieldName, reason, location, contact, pdfDate, subFilter,
                    "CMS is missing the signer certificate", notes);
            }
            var certificates = signedData.enter(0xa0);
            var certificate = parseCertificate(certificates.readEncoded());
            if (signedData.peekTag() == 0xa1) {
                signedData.enter(); // CRLs, unused
            }
            var signerInfos = signedData.enter(0x31);
            var signerInfo = signerInfos.enter(0x30);
            signerInfo.enter(); // version
            signerInfo.enter(); // sid
            signerInfo.enter(); // digestAlgorithm
            if (signerInfo.peekTag() != 0xa0) {
                return failure(fieldName, reason, location, contact, pdfDate, subFilter,
                    "CMS is missing signed attributes", notes);
            }
            var signedAttrs = signerInfo.enter(0xa0);
            var signedAttrsDer = signedAttrs.contentAsSet();
            var attributes = parseAttributes(signedAttrs);
            signerInfo.enter(); // signatureAlgorithm
            var signatureValue = signerInfo.enter(0x04).remaining();

            var digestMatches = Arrays.equals(expectedDigest, attributes.messageDigest());
            if (!digestMatches) {
                notes.add("messageDigest does not match the PDF ByteRange");
            }
            if (attributes.contentType() == null || !Arrays.equals(attributes.contentType(), Oids.DATA)) {
                notes.add("signed contentType is not id-data");
            }
            if (!attributes.hasSigningCertificateV2()) {
                notes.add("ESS signing-certificate-v2 is missing");
            } else if (!Arrays.equals(attributes.signingCertificateHash(), Digests.sha256(certificate.getEncoded()))) {
                notes.add("signing-certificate-v2 does not match the embedded certificate");
            }

            var algorithm = certificate.getPublicKey().getAlgorithm();
            var signatureAlgorithm = "EC".equalsFoldCase(algorithm) ? "SHA256withECDSA" : "SHA256withRSA";
            var signature = Signature.getInstance(signatureAlgorithm);
            signature.initVerify(certificate.getPublicKey());
            signature.update(signedAttrsDer);
            var signatureValid = signature.verify(signatureValue);
            if (!signatureValid) {
                notes.add("CMS signature value did not verify with the embedded certificate");
            }

            var profile = SignatureFieldProfiles.profileName(subFilter);
            notes.add("This is an advanced electronic signature (AdES) integrity seal, not a qualified eIDAS signature.");
            return new SignatureVerification(
                digestMatches,
                signatureValid,
                digestMatches && signatureValid,
                fieldName,
                reason,
                location,
                contact,
                pdfDate,
                subFilter,
                profile,
                certificate.getSubjectX500Principal().getName(),
                certificate.getIssuerX500Principal().getName(),
                HexFormat.of().formatHex(certificate.getSerialNumber().toByteArray()),
                "SHA-256",
                List.copyOf(notes)
            );
        } catch (Exception exception) {
            notes.add(exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage());
            return failure(fieldName, reason, location, contact, pdfDate, subFilter,
                "CMS could not be parsed", notes);
        }
    }

    private static SignatureVerification failure(String fieldName, String reason, String location,
                                                 String contact, String pdfDate, String subFilter,
                                                 String error, ArrayList<String> notes) {
        notes.add(error);
        return new SignatureVerification(false, false, false, fieldName, reason, location, contact,
            pdfDate, subFilter, SignatureFieldProfiles.profileName(subFilter),
            "", "", "", "SHA-256", List.copyOf(notes));
    }

    private static X509Certificate parseCertificate(byte[] encoded) throws GeneralSecurityException {
        return (X509Certificate) CertificateFactory.getInstance("X.509")
            .generateCertificate(new ByteArrayInputStream(encoded));
    }

    private static byte[] trimPadding(byte[] cms) {
        if (cms.length < 4 || cms[0] != 0x30) {
            throw new IllegalArgumentException("CMS does not start with a SEQUENCE");
        }
        var reader = DerReader.of(cms);
        return reader.readEncoded();
    }

    private static SignedAttributes parseAttributes(DerReader attributes) {
        byte[] contentType = null;
        byte[] messageDigest = null;
        byte[] signingCertificateHash = null;
        var hasSigningCertificate = false;
        for (var encoded : attributes.childrenEncoded()) {
            var attribute = DerReader.of(encoded).enter(0x30);
            var oid = attribute.oidEncoded();
            var values = attribute.enter(0x31);
            if (Arrays.equals(oid, Oids.CONTENT_TYPE)) {
                contentType = values.readEncoded();
            } else if (Arrays.equals(oid, Oids.MESSAGE_DIGEST)) {
                messageDigest = values.enter(0x04).remaining();
            } else if (Arrays.equals(oid, Oids.SIGNING_CERTIFICATE_V2)) {
                hasSigningCertificate = true;
                var signingCertificate = values.enter(0x30).enter(0x30).enter(0x30);
                if (signingCertificate.peekTag() == 0x30) {
                    signingCertificate.enter(); // hash algorithm
                }
                signingCertificateHash = signingCertificate.enter(0x04).remaining();
            }
        }
        return new SignedAttributes(contentType, messageDigest, hasSigningCertificate, signingCertificateHash);
    }

    private record SignedAttributes(
        byte[] contentType,
        byte[] messageDigest,
        boolean hasSigningCertificateV2,
        byte[] signingCertificateHash
    ) {
    }
}
