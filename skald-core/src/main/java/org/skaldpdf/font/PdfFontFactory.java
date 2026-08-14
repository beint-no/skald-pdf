package org.skaldpdf.font;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/** Creates embeddable PDF fonts from OpenType programs. */
public final class PdfFontFactory {
    private PdfFontFactory() {
    }

    public static PdfFont from(byte[] openTypeProgram, FontWeight weight) {
        return new PdfFont(new TrueTypeFont(Objects.requireNonNull(openTypeProgram, "openTypeProgram")),
            Objects.requireNonNull(weight, "weight"));
    }

    public static PdfFont from(Path openTypeFile, FontWeight weight) {
        try {
            return from(Files.readAllBytes(Objects.requireNonNull(openTypeFile, "openTypeFile")), weight);
        } catch (IOException exception) {
            throw new IllegalArgumentException("Unable to read OpenType font", exception);
        }
    }

}
