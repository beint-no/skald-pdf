package org.skaldpdf.sign.internal;

import org.skaldpdf.pdf.SignatureField;

public final class SignatureFieldProfiles {
    private SignatureFieldProfiles() {
    }

    public static String profileName(String subFilter) {
        if (SignatureField.PADES_B_B.equals(subFilter)) {
            return "PAdES-B-B";
        }
        return "PAdES-B-B attributes in an Adobe.PPKLite envelope";
    }
}
