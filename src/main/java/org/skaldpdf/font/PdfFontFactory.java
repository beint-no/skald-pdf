package org.skaldpdf.font;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/** Access to the bundled, embeddable Skald Sans faces. */
public final class PdfFontFactory {
    private static final PdfFont REGULAR = load("SkaldSans-Regular.ttf", FontWeight.REGULAR);
    private static final PdfFont BOLD = load("SkaldSans-Bold.ttf", FontWeight.BOLD);

    private PdfFontFactory() {
    }

    public static PdfFont regular() {
        return REGULAR;
    }

    public static PdfFont bold() {
        return BOLD;
    }

    public static PdfFont create(FontWeight weight) {
        return Objects.requireNonNull(weight, "weight") == FontWeight.BOLD ? BOLD : REGULAR;
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

    private static PdfFont load(String resource, FontWeight weight) {
        try (var input = PdfFontFactory.class.getResourceAsStream(resource)) {
            if (input == null) {
                throw new IllegalStateException("Missing bundled font: " + resource);
            }
            return new PdfFont(new TrueTypeFont(input.readAllBytes()), weight);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to load bundled font", exception);
        }
    }
}
