package com.desitech.vyaparsathi.common.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Converts a BigDecimal amount to Indian-numeric English words.
 *
 * <p>Example: {@code 1_23_456.78} → {@code "Rupees One Lakh Twenty Three
 * Thousand Four Hundred Fifty Six and Paise Seventy Eight Only"}.
 *
 * <p>Follows the Indian lakh/crore grouping (not the American million/billion)
 * as expected by CBIC-formatted invoices.
 */
public final class AmountInWordsIndian {

    private static final String[] ONES = {
            "", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine",
            "Ten", "Eleven", "Twelve", "Thirteen", "Fourteen", "Fifteen",
            "Sixteen", "Seventeen", "Eighteen", "Nineteen"
    };
    private static final String[] TENS = {
            "", "", "Twenty", "Thirty", "Forty", "Fifty",
            "Sixty", "Seventy", "Eighty", "Ninety"
    };

    private AmountInWordsIndian() {}

    /** Convert amount to words. Null / zero → "Rupees Zero Only". */
    public static String toWords(BigDecimal amount) {
        BigDecimal safe = amount == null ? BigDecimal.ZERO : amount;
        BigDecimal rounded = safe.setScale(2, RoundingMode.HALF_UP);

        long rupees = rounded.longValue();
        int paise = rounded.subtract(BigDecimal.valueOf(rupees))
                           .movePointRight(2)
                           .abs()
                           .intValue();

        StringBuilder sb = new StringBuilder("Rupees ");
        if (rupees < 0) {
            sb.append("Minus ");
            rupees = -rupees;
        }
        sb.append(rupees == 0 ? "Zero" : convertRupees(rupees));

        if (paise > 0) {
            sb.append(" and Paise ").append(convertUnderHundred(paise));
        }
        sb.append(" Only");
        return sb.toString().trim().replaceAll("\\s+", " ");
    }

    private static String convertRupees(long n) {
        StringBuilder result = new StringBuilder();
        long crore = n / 10_000_000L;
        long lakh  = (n / 100_000L) % 100L;
        long thousand = (n / 1_000L) % 100L;
        long hundred  = (n / 100L) % 10L;
        long rest     = n % 100L;

        if (crore > 0) {
            result.append(convertUnderHundred(crore)).append(" Crore ");
        }
        if (lakh > 0) {
            result.append(convertUnderHundred(lakh)).append(" Lakh ");
        }
        if (thousand > 0) {
            result.append(convertUnderHundred(thousand)).append(" Thousand ");
        }
        if (hundred > 0) {
            result.append(ONES[(int) hundred]).append(" Hundred ");
        }
        if (rest > 0) {
            if (hundred > 0 || thousand > 0 || lakh > 0 || crore > 0) {
                result.append("and ");
            }
            result.append(convertUnderHundred(rest));
        }
        return result.toString().trim();
    }

    private static String convertUnderHundred(long n) {
        if (n < 20) return ONES[(int) n];
        long tens = n / 10;
        long ones = n % 10;
        return (TENS[(int) tens] + (ones > 0 ? " " + ONES[(int) ones] : "")).trim();
    }
}
