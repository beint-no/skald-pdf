package org.skaldpdf.sign.internal;

/** Object identifiers used by the CMS / X.509 subset Skald emits. */
public final class Oids {
    public static final byte[] SIGNED_DATA = Der.oid(1, 2, 840, 113549, 1, 7, 2);
    public static final byte[] DATA = Der.oid(1, 2, 840, 113549, 1, 7, 1);
    public static final byte[] CONTENT_TYPE = Der.oid(1, 2, 840, 113549, 1, 9, 3);
    public static final byte[] MESSAGE_DIGEST = Der.oid(1, 2, 840, 113549, 1, 9, 4);
    public static final byte[] SIGNING_TIME = Der.oid(1, 2, 840, 113549, 1, 9, 5);
    public static final byte[] SIGNING_CERTIFICATE_V2 = Der.oid(1, 2, 840, 113549, 1, 9, 16, 2, 47);
    public static final byte[] SHA_256 = Der.oid(2, 16, 840, 1, 101, 3, 4, 2, 1);
    public static final byte[] RSA_ENCRYPTION = Der.oid(1, 2, 840, 113549, 1, 1, 1);
    public static final byte[] SHA256_WITH_RSA = Der.oid(1, 2, 840, 113549, 1, 1, 11);
    public static final byte[] EC_PUBLIC_KEY = Der.oid(1, 2, 840, 10045, 2, 1);
    public static final byte[] ECDSA_WITH_SHA256 = Der.oid(1, 2, 840, 10045, 4, 3, 2);
    public static final byte[] COMMON_NAME = Der.oid(2, 5, 4, 3);
    public static final byte[] ORGANIZATION_NAME = Der.oid(2, 5, 4, 10);
    public static final byte[] COUNTRY_NAME = Der.oid(2, 5, 4, 6);
    public static final byte[] KEY_USAGE = Der.oid(2, 5, 29, 15);
    public static final byte[] BASIC_CONSTRAINTS = Der.oid(2, 5, 29, 19);

    private Oids() {
    }
}
