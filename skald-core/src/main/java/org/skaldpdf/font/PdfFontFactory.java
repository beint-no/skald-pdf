package org.skaldpdf.font;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/** Access to the bundled, embeddable Skald Sans faces. */
public final class PdfFontFactory {
    private static final PdfFont REGULAR = load("SkaldSans-Regular.ttf", FontWeight.REGULAR);
    private static final PdfFont BOLD = load("SkaldSans-Bold.ttf", FontWeight.BOLD);
    private static final PdfFont ITALIC = load("SkaldSans-Italic.ttf", FontWeight.ITALIC);
    private static final PdfFont BOLD_ITALIC = load("SkaldSans-BoldItalic.ttf", FontWeight.BOLD_ITALIC);

    private PdfFontFactory() {
    }

    public static PdfFont regular() {
        return REGULAR;
    }

    public static PdfFont bold() {
        return BOLD;
    }

    public static PdfFont italic() {
        return ITALIC;
    }

    public static PdfFont boldItalic() {
        return BOLD_ITALIC;
    }

    public static PdfFont create(FontWeight weight) {
        return switch (Objects.requireNonNull(weight, "weight")) {
            case BOLD -> BOLD;
            case ITALIC -> ITALIC;
            case BOLD_ITALIC -> BOLD_ITALIC;
            case REGULAR -> REGULAR;
        };
    }

    public static PdfFont create(boolean bold, boolean italic) {
        if (bold && italic) {
            return BOLD_ITALIC;
        }
        if (bold) {
            return BOLD;
        }
        return italic ? ITALIC : REGULAR;
    }

    public static boolean bundled(PdfFont font) {
        return font == REGULAR || font == BOLD || font == ITALIC || font == BOLD_ITALIC;
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
