package com.desitech.vyaparsathi.common.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class AmountInWordsIndianTest {

    @Test
    @DisplayName("Zero / null → 'Rupees Zero Only'")
    void zeroCase() {
        assertEquals("Rupees Zero Only", AmountInWordsIndian.toWords(BigDecimal.ZERO));
        assertEquals("Rupees Zero Only", AmountInWordsIndian.toWords(null));
    }

    @Test
    @DisplayName("Single digit")
    void singleDigit() {
        assertEquals("Rupees Seven Only", AmountInWordsIndian.toWords(new BigDecimal("7")));
    }

    @Test
    @DisplayName("Teen — the 11-19 special names must fire")
    void teen() {
        assertEquals("Rupees Fourteen Only", AmountInWordsIndian.toWords(new BigDecimal("14")));
        assertEquals("Rupees Nineteen Only", AmountInWordsIndian.toWords(new BigDecimal("19")));
    }

    @Test
    @DisplayName("Two digit with tens + ones")
    void twoDigits() {
        assertEquals("Rupees Forty Two Only", AmountInWordsIndian.toWords(new BigDecimal("42")));
    }

    @Test
    @DisplayName("Hundred and rest — 'and' bridge is inserted")
    void hundredWithRest() {
        assertEquals("Rupees One Hundred and Twenty Three Only",
                AmountInWordsIndian.toWords(new BigDecimal("123")));
    }

    @Test
    @DisplayName("Indian lakh grouping — 1,23,456")
    void indianLakhGrouping() {
        String words = AmountInWordsIndian.toWords(new BigDecimal("123456"));
        assertTrue(words.contains("Lakh"), "Expected lakh grouping; got: " + words);
        assertTrue(words.contains("Twenty Three Thousand"),
                "Expected 'Twenty Three Thousand' segment; got: " + words);
    }

    @Test
    @DisplayName("Crore grouping — 1,00,00,000")
    void crore() {
        String words = AmountInWordsIndian.toWords(new BigDecimal("10000000"));
        assertTrue(words.contains("One Crore"), "Expected crore segment; got: " + words);
    }

    @Test
    @DisplayName("Paise are rendered with 'and Paise' bridge")
    void withPaise() {
        String words = AmountInWordsIndian.toWords(new BigDecimal("100.50"));
        assertTrue(words.contains("and Paise Fifty"),
                "Expected paise bridge; got: " + words);
    }

    @Test
    @DisplayName("Paise round HALF_UP so 100.505 → 51 paise")
    void paiseHalfUp() {
        String words = AmountInWordsIndian.toWords(new BigDecimal("100.505"));
        assertTrue(words.contains("Fifty One"),
                "Expected HALF_UP rounding to 51; got: " + words);
    }

    @Test
    @DisplayName("Ends with ' Only' and never has doubled spaces")
    void formatDiscipline() {
        String words = AmountInWordsIndian.toWords(new BigDecimal("1234.56"));
        assertTrue(words.endsWith(" Only"), "Should terminate with ' Only': " + words);
        assertFalse(words.contains("  "), "Should not contain double spaces: " + words);
    }

    @Test
    @DisplayName("Negative amount gets 'Minus' prefix")
    void negative() {
        String words = AmountInWordsIndian.toWords(new BigDecimal("-50"));
        assertTrue(words.contains("Minus"), "Expected 'Minus' prefix; got: " + words);
        assertTrue(words.contains("Fifty"), "Expected magnitude words; got: " + words);
    }
}
