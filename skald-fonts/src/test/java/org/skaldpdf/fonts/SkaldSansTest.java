package org.skaldpdf.fonts;

import org.junit.jupiter.api.Test;
import org.skaldpdf.font.FontWeight;
import org.skaldpdf.font.PdfFontFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkaldSansTest {
    @Test
    void loadsAndResolvesEachFaceIndependently() throws Exception {
        assertEquals(0, SkaldSans.loadedFaceCount());
        assertSame(SkaldSans.regular(), SkaldSans.create(FontWeight.REGULAR));
        assertEquals(1, SkaldSans.loadedFaceCount());
        assertSame(SkaldSans.bold(), SkaldSans.create(FontWeight.BOLD));
        assertEquals(2, SkaldSans.loadedFaceCount());
        assertSame(SkaldSans.italic(), SkaldSans.create(FontWeight.ITALIC));
        assertEquals(3, SkaldSans.loadedFaceCount());
        assertSame(SkaldSans.boldItalic(), SkaldSans.create(FontWeight.BOLD_ITALIC));
        assertEquals(4, SkaldSans.loadedFaceCount());
        assertTrue(SkaldSans.isFace(SkaldSans.regular()));
        try (var input = SkaldSans.class.getResourceAsStream("SkaldSans-Regular.ttf")) {
            assertFalse(SkaldSans.isFace(PdfFontFactory.from(input.readAllBytes(), FontWeight.REGULAR)));
        }
    }
}
