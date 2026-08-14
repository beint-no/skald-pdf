package org.skaldpdf.fonts;

import org.skaldpdf.font.FontWeight;
import org.skaldpdf.font.PdfFont;
import org.skaldpdf.font.PdfFontFactory;

import java.io.IOException;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Access to the bundled, embeddable Skald Sans faces. */
public final class SkaldSans {
    private static final Set<PdfFont> LOADED_FACES = ConcurrentHashMap.newKeySet();

    private SkaldSans() {
    }

    public static PdfFont regular() {
        return Regular.FONT;
    }

    public static PdfFont bold() {
        return Bold.FONT;
    }

    public static PdfFont italic() {
        return Italic.FONT;
    }

    public static PdfFont boldItalic() {
        return BoldItalic.FONT;
    }

    public static PdfFont create(FontWeight weight) {
        return switch (Objects.requireNonNull(weight, "weight")) {
            case BOLD -> bold();
            case ITALIC -> italic();
            case BOLD_ITALIC -> boldItalic();
            case REGULAR -> regular();
        };
    }

    public static PdfFont create(boolean bold, boolean italic) {
        if (bold && italic) {
            return boldItalic();
        }
        if (bold) {
            return bold();
        }
        return italic ? italic() : regular();
    }

    public static boolean isFace(PdfFont font) {
        return LOADED_FACES.contains(Objects.requireNonNull(font, "font"));
    }

    static int loadedFaceCount() {
        return LOADED_FACES.size();
    }

    private static PdfFont load(String resource, FontWeight weight) {
        try (var input = SkaldSans.class.getResourceAsStream(resource)) {
            if (input == null) {
                throw new IllegalStateException("Missing bundled font: " + resource);
            }
            var font = PdfFontFactory.from(input.readAllBytes(), weight);
            LOADED_FACES.add(font);
            return font;
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to load bundled font", exception);
        }
    }

    private static final class Regular {
        private static final PdfFont FONT = load("SkaldSans-Regular.ttf", FontWeight.REGULAR);
    }

    private static final class Bold {
        private static final PdfFont FONT = load("SkaldSans-Bold.ttf", FontWeight.BOLD);
    }

    private static final class Italic {
        private static final PdfFont FONT = load("SkaldSans-Italic.ttf", FontWeight.ITALIC);
    }

    private static final class BoldItalic {
        private static final PdfFont FONT = load("SkaldSans-BoldItalic.ttf", FontWeight.BOLD_ITALIC);
    }
}
