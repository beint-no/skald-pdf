package org.skaldpdf.sign;

import org.skaldpdf.sign.internal.Oids;
import org.skaldpdf.sign.internal.X509SelfSigned;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PEMDecoder;
import java.security.PEMEncoder;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A private key and X.509 certificate used to seal a PDF. Load a PKCS#12 issued
 * by your CA, or create a self-signed development key. Self-signed keys are
 * integrity seals, not qualified certificates.
 */
public final class SigningKey {
    private final PrivateKey privateKey;
    private final List<X509Certificate> chain;

    private SigningKey(PrivateKey privateKey, List<X509Certificate> chain) {
        this.privateKey = Objects.requireNonNull(privateKey, "privateKey");
        if (chain.isEmpty()) {
            throw new IllegalArgumentException("A signing key needs a certificate");
        }
        this.chain = List.copyOf(chain);
    }

    public static SigningKey of(PrivateKey privateKey, X509Certificate... certificates) {
        return new SigningKey(privateKey, List.of(certificates));
    }

    /**
     * Loads an unencrypted PKCS#8 private key and an X.509 certificate from PEM.
     * Uses the JDK 26 {@link PEMDecoder} preview API.
     */
    public static SigningKey fromPem(String privateKeyPem, String certificatePem) {
        return fromPem(privateKeyPem, null, certificatePem);
    }

    /**
     * Loads a PEM private key (optionally encrypted) and an X.509 certificate.
     * Uses the JDK 26 {@link PEMDecoder} preview API.
     */
    public static SigningKey fromPem(String privateKeyPem, char[] password, String certificatePem) {
        Objects.requireNonNull(privateKeyPem, "privateKeyPem");
        Objects.requireNonNull(certificatePem, "certificatePem");
        try {
            var decoder = password == null || password.length == 0
                ? PEMDecoder.of()
                : PEMDecoder.of().withDecryption(password);
            var privateKey = decoder.decode(privateKeyPem, PrivateKey.class);
            var certificate = PEMDecoder.of().decode(certificatePem, X509Certificate.class);
            return new SigningKey(privateKey, List.of(certificate));
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Unable to decode PEM signing key", exception);
        }
    }

    /** PKCS#8 private key as PEM text. */
    public String privateKeyPem() {
        return PEMEncoder.of().encodeToString(privateKey);
    }

    /** Leaf certificate as PEM text. */
    public String certificatePem() {
        return PEMEncoder.of().encodeToString(certificate());
    }

    public static SigningKey fromPkcs12(Path path, char[] password) {
        try (var input = Files.newInputStream(path)) {
            return fromPkcs12(input, password);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to read PKCS#12 keystore " + path, exception);
        }
    }

    public static SigningKey fromPkcs12(InputStream input, char[] password) {
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(password, "password");
        try {
            var store = KeyStore.getInstance("PKCS12");
            store.load(input, password);
            var aliases = store.aliases();
            while (aliases.hasMoreElements()) {
                var alias = aliases.nextElement();
                if (!store.isKeyEntry(alias)) {
                    continue;
                }
                var key = store.getKey(alias, password);
                if (!(key instanceof PrivateKey privateKey)) {
                    continue;
                }
                var chain = new ArrayList<X509Certificate>();
                var certificates = store.getCertificateChain(alias);
                if (certificates == null) {
                    continue;
                }
                for (var certificate : certificates) {
                    if (certificate instanceof X509Certificate x509) {
                        chain.add(x509);
                    }
                }
                if (!chain.isEmpty()) {
                    return new SigningKey(privateKey, chain);
                }
            }
            throw new IllegalArgumentException("PKCS#12 keystore does not contain a private key and certificate");
        } catch (GeneralSecurityException | IOException exception) {
            throw new IllegalStateException("Unable to load PKCS#12 signing key", exception);
        }
    }

    /**
     * RSA-2048 self-signed certificate for tests and local development.
     * Not a qualified certificate and not a substitute for a CA-issued key.
     */
    public static SigningKey selfSigned(String commonName) {
        return selfSigned(commonName, "Skald", "NO");
    }

    public static SigningKey selfSigned(String commonName, String organization, String country) {
        try {
            var generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            var keyPair = generator.generateKeyPair();
            var now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
            var certificate = X509SelfSigned.create(
                keyPair, commonName, organization, country, now.minus(1, ChronoUnit.DAYS), now.plus(3650, ChronoUnit.DAYS));
            return new SigningKey(keyPair.getPrivate(), List.of(certificate));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Unable to generate a self-signed signing key", exception);
        }
    }

    public void storePkcs12(Path path, char[] password) {
        try (var output = Files.newOutputStream(path)) {
            storePkcs12(output, password);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to write PKCS#12 keystore " + path, exception);
        }
    }

    public void storePkcs12(OutputStream output, char[] password) {
        Objects.requireNonNull(output, "output");
        Objects.requireNonNull(password, "password");
        try {
            var store = KeyStore.getInstance("PKCS12");
            store.load(null, null);
            store.setKeyEntry("signing", privateKey, password, chain.toArray(Certificate[]::new));
            store.store(output, password);
        } catch (GeneralSecurityException | IOException exception) {
            throw new IllegalStateException("Unable to write PKCS#12 keystore", exception);
        }
    }

    public PrivateKey privateKey() {
        return privateKey;
    }

    public X509Certificate certificate() {
        return chain.getFirst();
    }

    public List<X509Certificate> chain() {
        return chain;
    }

    public String signatureAlgorithm() {
        return "EC".equalsIgnoreCase(privateKey.getAlgorithm()) ? "SHA256withECDSA" : "SHA256withRSA";
    }

    public byte[] signatureEncryptionOid() {
        return "EC".equalsIgnoreCase(privateKey.getAlgorithm()) ? Oids.ECDSA_WITH_SHA256 : Oids.RSA_ENCRYPTION;
    }
}
