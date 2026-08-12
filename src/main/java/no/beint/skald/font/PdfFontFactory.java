package no.beint.skald.font;

public final class PdfFontFactory {
    private static final PdfFont REGULAR = new PdfFont(StandardFonts.HELVETICA);
    private static final PdfFont BOLD = new PdfFont(StandardFonts.HELVETICA_BOLD);

    private PdfFontFactory() {
    }

    public static PdfFont createFont() {
        return REGULAR;
    }

    public static PdfFont createFont(String name) {
        return StandardFonts.HELVETICA_BOLD.equals(name) ? BOLD : REGULAR;
    }

    public static PdfFont regular() {
        return REGULAR;
    }

    public static PdfFont bold() {
        return BOLD;
    }
}
