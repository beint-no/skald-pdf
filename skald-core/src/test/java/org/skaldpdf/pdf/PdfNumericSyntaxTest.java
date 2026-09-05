package org.skaldpdf.pdf;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PdfNumericSyntaxTest {
    // Keep the previous implementation's grammar as an independent oracle.
    private static final Pattern LEGACY_NUMBER = Pattern.compile("[+-]?(?:\\d+(?:\\.\\d*)?|\\.\\d+)");
    private static final Pattern LEGACY_INTEGER = Pattern.compile("[+-]?\\d+");
    private static final char[] ALPHABET = { '0', '9', '+', '-', '.', 'e', ' ', '\n', '\u0660', '\uD800' };

    @Test
    void matchesThePreviousGrammarForEveryShortCombination() {
        for (int length = 0; length <= 5; length++) {
            checkCombinations(new char[length], 0);
        }
    }

    @Test
    void onlyAsciiDigitsAndPdfPunctuationAreAccepted() {
        for (int character = Character.MIN_VALUE; character <= Character.MAX_VALUE; character++) {
            assertEquivalent("1" + (char) character + "2");
        }
        for (var token : List.of("1234567890", "+1234567890.0123456789", "-.0123456789",
            "1e2", "0x10", "NaN", "Infinity", "1\r", "1\t", "\uD835\uDFD8")) {
            assertEquivalent(token);
        }
    }

    @Test
    void handlesLongTokensWithoutChangingTheGrammar() {
        var digits = "1234567890".repeat(3_000);
        assertTrue(PdfNumbers.isInteger("-" + digits));
        assertTrue(PdfNumbers.isNumber("+." + digits));
        assertTrue(PdfNumbers.isNumber(digits + "."));
        assertFalse(PdfNumbers.isInteger(digits + "."));
        assertFalse(PdfNumbers.isNumber(digits + ".0."));
        assertFalse(PdfNumbers.isNumber(digits + "e1"));
    }

    private static void checkCombinations(char[] token, int index) {
        if (index == token.length) {
            assertEquivalent(new String(token));
            return;
        }
        for (var character : ALPHABET) {
            token[index] = character;
            checkCombinations(token, index + 1);
        }
    }

    private static void assertEquivalent(String token) {
        assertEquals(LEGACY_NUMBER.matcher(token).matches(), PdfNumbers.isNumber(token), () -> "number: " + token);
        assertEquals(LEGACY_INTEGER.matcher(token).matches(), PdfNumbers.isInteger(token), () -> "integer: " + token);
    }
}
