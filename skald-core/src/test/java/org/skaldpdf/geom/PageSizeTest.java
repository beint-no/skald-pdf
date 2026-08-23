package org.skaldpdf.geom;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PageSizeTest {
    @Test
    void rejectsNonPositiveDimensionsBeforeConstruction() {
        var zero = assertThrows(IllegalArgumentException.class, () -> new PageSize(0, 100));
        assertTrue(zero.getMessage().contains("positive"));
        var negative = assertThrows(IllegalArgumentException.class, () -> new PageSize(-10, 100));
        assertTrue(negative.getMessage().contains("positive"));
    }

    @Test
    void millimetreFactoryProducesPositivePages() {
        var label = PageSize.ofMillimetres(93, 35);
        assertEquals(93 * 72f / 25.4f, label.getWidth(), 0.01f);
        assertEquals(35 * 72f / 25.4f, label.getHeight(), 0.01f);
    }
}
