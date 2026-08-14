package org.skaldpdf.pdf;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;

/**
 * ISO 32000-2 revision 6 (AES-256) Standard Security Handler. Write-only:
 * Skald does not decrypt received files.
 */
final class Revision6Encryption {
    private static final String[] HASHES = { "SHA-256", "SHA-384", "SHA-512" };
    private final byte[] fileKey;
    private final byte[] userKey;
    private final byte[] userEncryptionKey;
    private final byte[] ownerKey;
    private final byte[] ownerEncryptionKey;
    private final byte[] perms;
    private final int permissions;
    private final SecureRandom random = new SecureRandom();

    Revision6Encryption(PdfEncryption policy) {
        this.permissions = policy.permissions();
        this.fileKey = randomBytes(32);
        var userPassword = truncate127(policy.userPassword().getBytes(StandardCharsets.UTF_8));
        var ownerPassword = truncate127(policy.ownerPassword().getBytes(StandardCharsets.UTF_8));
        try {
            var userValidationSalt = randomBytes(8);
            var userKeySalt = randomBytes(8);
            var hashU = computeHash2B(concat(userPassword, userValidationSalt), userPassword, null);
            this.userKey = concat(hashU, userValidationSalt, userKeySalt);
            var hashUe = computeHash2B(concat(userPassword, userKeySalt), userPassword, null);
            this.userEncryptionKey = aesCbcZeroIv(hashUe, fileKey);

            var ownerValidationSalt = randomBytes(8);
            var ownerKeySalt = randomBytes(8);
            var hashO = computeHash2B(concat(ownerPassword, ownerValidationSalt, userKey), ownerPassword, userKey);
            this.ownerKey = concat(hashO, ownerValidationSalt, ownerKeySalt);
            var hashOe = computeHash2B(concat(ownerPassword, ownerKeySalt, userKey), ownerPassword, userKey);
            this.ownerEncryptionKey = aesCbcZeroIv(hashOe, fileKey);

            var permBytes = new byte[16];
            random.nextBytes(permBytes);
            permBytes[0] = (byte) permissions;
            permBytes[1] = (byte) (permissions >>> 8);
            permBytes[2] = (byte) (permissions >>> 16);
            permBytes[3] = (byte) (permissions >>> 24);
            permBytes[4] = (byte) 0xff;
            permBytes[5] = (byte) 0xff;
            permBytes[6] = (byte) 0xff;
            permBytes[7] = (byte) 0xff;
            permBytes[8] = 'T';
            permBytes[9] = 'a';
            permBytes[10] = 'd';
            permBytes[11] = 'b';
            this.perms = aesCbcZeroIv(fileKey, permBytes);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("AES-256 is not available", exception);
        }
    }

    byte[] encrypt(byte[] plaintext) {
        try {
            var iv = randomBytes(16);
            var cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(fileKey, "AES"), new IvParameterSpec(iv));
            var ciphertext = cipher.doFinal(plaintext);
            return concat(iv, ciphertext);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Unable to encrypt PDF bytes", exception);
        }
    }

    String dictionary() {
        return "/Filter /Standard /V 5 /R 6 /Length 256 /P " + permissions
            + " /EncryptMetadata true"
            + " /CF << /StdCF << /Type /CryptFilter /CFM /AESV3 /AuthEvent /DocOpen /Length 32 >> >>"
            + " /StmF /StdCF /StrF /StdCF"
            + " /U <" + hex(userKey) + ">"
            + " /O <" + hex(ownerKey) + ">"
            + " /UE <" + hex(userEncryptionKey) + ">"
            + " /OE <" + hex(ownerEncryptionKey) + ">"
            + " /Perms <" + hex(perms) + ">";
    }

    private byte[] randomBytes(int length) {
        var bytes = new byte[length];
        random.nextBytes(bytes);
        return bytes;
    }

    private static byte[] aesCbcZeroIv(byte[] key, byte[] plaintext) throws GeneralSecurityException {
        var cipher = Cipher.getInstance("AES/CBC/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(new byte[16]));
        return cipher.doFinal(plaintext);
    }

    private static byte[] computeHash2B(byte[] input, byte[] password, byte[] userKey)
        throws GeneralSecurityException {
        var digest = MessageDigest.getInstance("SHA-256");
        var k = digest.digest(input);
        byte[] encrypted = null;
        for (int round = 0; round < 64 || (encrypted[encrypted.length - 1] & 0xff) > round - 32; round++) {
            var block = 64 * (password.length + k.length + (userKey == null ? 0 : 48));
            var k1 = new byte[block];
            var position = 0;
            for (int copy = 0; copy < 64; copy++) {
                System.arraycopy(password, 0, k1, position, password.length);
                position += password.length;
                System.arraycopy(k, 0, k1, position, k.length);
                position += k.length;
                if (userKey != null) {
                    System.arraycopy(userKey, 0, k1, position, 48);
                    position += 48;
                }
            }
            var cipher = Cipher.getInstance("AES/CBC/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(Arrays.copyOf(k, 16), "AES"),
                new IvParameterSpec(Arrays.copyOfRange(k, 16, 32)));
            encrypted = cipher.doFinal(k1);
            var remainder = new BigInteger(1, Arrays.copyOf(encrypted, 16)).mod(BigInteger.valueOf(3)).intValue();
            digest = MessageDigest.getInstance(HASHES[remainder]);
            k = digest.digest(encrypted);
        }
        return k.length == 32 ? k : Arrays.copyOf(k, 32);
    }

    private static byte[] truncate127(byte[] bytes) {
        return bytes.length <= 127 ? bytes : Arrays.copyOf(bytes, 127);
    }

    private static byte[] concat(byte[]... parts) {
        var length = 0;
        for (var part : parts) {
            length += part.length;
        }
        var result = new byte[length];
        var offset = 0;
        for (var part : parts) {
            System.arraycopy(part, 0, result, offset, part.length);
            offset += part.length;
        }
        return result;
    }

    private static String hex(byte[] bytes) {
        var result = new StringBuilder(bytes.length * 2);
        for (var item : bytes) {
            result.append(String.format("%02X", item & 0xff));
        }
        return result.toString();
    }
}
