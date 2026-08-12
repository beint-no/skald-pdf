package no.beint.skald.font;

import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

import java.io.IOException;

public final class PdfFont {
    private final String name;
    private final boolean bold;
    private final PDFont pdfBoxFont;

    PdfFont(String name) {
        this.name = name;
        this.bold = StandardFonts.HELVETICA_BOLD.equals(name);
        var fontName = bold
            ? org.apache.pdfbox.pdmodel.font.Standard14Fonts.FontName.HELVETICA_BOLD
            : org.apache.pdfbox.pdmodel.font.Standard14Fonts.FontName.HELVETICA;
        this.pdfBoxFont = new PDType1Font(fontName);
    }

    public String name() {
        return name;
    }

    public boolean bold() {
        return bold;
    }

    public PDFont pdfBoxFont() {
        return pdfBoxFont;
    }

    public float getWidth(String text, float fontSize) {
        try {
            return pdfBoxFont.getStringWidth(supportedText(text)) / 1000f * fontSize;
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to measure text", exception);
        }
    }

    public String supportedText(String text) {
        var result = new StringBuilder(text.length());
        text.codePoints().forEach(codePoint -> {
            var character = new String(Character.toChars(codePoint));
            try {
                pdfBoxFont.getStringWidth(character);
                result.append(character);
            } catch (IOException | IllegalArgumentException exception) {
                result.append('?');
            }
        });
        return result.toString();
    }
}
