package com.desitech.vyaparsathi.gst.util;

import com.desitech.vyaparsathi.sales.enums.GSTType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("GstTaxCalculator")
class GstTaxCalculatorTest {

    // ── computeLineGst ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("computeLineGst — CGST/SGST odd-paise split")
    class OddPaiseSplit {

        @Test
        @DisplayName("18% on ₹100 — exact split")
        void gst18_exactSplit() {
            GstTaxCalculator.LineGst lg = GstTaxCalculator.computeLineGst(
                    bd("100.00"), GSTType.GST_18, BigDecimal.ZERO, true, false);
            // Total GST = 18, split = 9 + 9
            assertEquals(bd("9.00"), lg.cgst());
            assertEquals(bd("9.00"), lg.sgst());
            assertEquals(BigDecimal.ZERO, lg.igst());
            assertEquals(BigDecimal.ZERO, lg.cess());
            assertEquals(bd("18.00"), lg.totalGst());
        }

        @Test
        @DisplayName("18% on ₹0.06 — odd paise: cgst + sgst must equal totalGst")
        void gst18_oddPaise_6paise() {
            // taxable = 0.06, gst = 18% = 0.0108 → rounds to 0.01
            // floor(0.01 / 2) = 0.00; complement = 0.01 - 0.00 = 0.01
            // cgst = 0.00, sgst = 0.01 — sum = 0.01 ✓
            GstTaxCalculator.LineGst lg = GstTaxCalculator.computeLineGst(
                    bd("0.06"), GSTType.GST_18, BigDecimal.ZERO, true, false);
            BigDecimal sumHalves = lg.cgst().add(lg.sgst());
            assertEquals(lg.totalGst(), sumHalves,
                    "cgst + sgst must equal totalGst (no paise gap)");
        }

        @Test
        @DisplayName("5% on ₹1.00 — odd paise: floor + complement")
        void gst5_oddPaise() {
            // taxable = 1.00, gst = 0.05
            // floor(0.05 / 2) = 0.02; complement = 0.05 - 0.02 = 0.03
            GstTaxCalculator.LineGst lg = GstTaxCalculator.computeLineGst(
                    bd("1.00"), GSTType.GST_5, BigDecimal.ZERO, true, false);
            assertEquals(bd("0.02"), lg.cgst());
            assertEquals(bd("0.03"), lg.sgst());
            assertEquals(lg.totalGst(), lg.cgst().add(lg.sgst()));
        }

        @Test
        @DisplayName("12% on ₹1.00 — even paise: both halves equal")
        void gst12_evenPaise() {
            GstTaxCalculator.LineGst lg = GstTaxCalculator.computeLineGst(
                    bd("1.00"), GSTType.GST_12, BigDecimal.ZERO, true, false);
            // 12% on 1.00 = 0.12 → cgst=0.06, sgst=0.06
            assertEquals(bd("0.06"), lg.cgst());
            assertEquals(bd("0.06"), lg.sgst());
        }

        @Test
        @DisplayName("Union Territory: CGST + UTGST, not SGST")
        void unionTerritory_usesUtgst() {
            GstTaxCalculator.LineGst lg = GstTaxCalculator.computeLineGst(
                    bd("100.00"), GSTType.GST_18, BigDecimal.ZERO, true, true);
            assertEquals(bd("9.00"), lg.cgst());
            assertEquals(bd("9.00"), lg.utgst());
            assertEquals(BigDecimal.ZERO, lg.sgst());
        }

        @Test
        @DisplayName("Inter-state: IGST only")
        void interState_igstOnly() {
            GstTaxCalculator.LineGst lg = GstTaxCalculator.computeLineGst(
                    bd("100.00"), GSTType.GST_18, BigDecimal.ZERO, false, false);
            assertEquals(bd("18.00"), lg.igst());
            assertEquals(BigDecimal.ZERO, lg.cgst());
            assertEquals(BigDecimal.ZERO, lg.sgst());
        }

        @Test
        @DisplayName("Zero-rate line: all tax components are zero")
        void zeroRate_allZero() {
            GstTaxCalculator.LineGst lg = GstTaxCalculator.computeLineGst(
                    bd("1000.00"), GSTType.GST_0, BigDecimal.ZERO, true, false);
            assertEquals(BigDecimal.ZERO, lg.totalGst());
            assertEquals(BigDecimal.ZERO, lg.cess());
        }

        @Test
        @DisplayName("Cess 1% on ₹100 — cess is separate from GST total")
        void cessTrackedSeparately() {
            GstTaxCalculator.LineGst lg = GstTaxCalculator.computeLineGst(
                    bd("100.00"), GSTType.GST_28, bd("1.00"), true, false);
            assertEquals(bd("1.00"), lg.cess());
            // totalGst() excludes cess; totalTax() includes it
            assertEquals(bd("28.00"), lg.totalGst());
            assertEquals(bd("29.00"), lg.totalTax());
        }
    }

    // ── allocateDiscount ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("allocateDiscount — Section 15(3)(b) proportional allocation")
    class AllocateDiscount {

        @Test
        @DisplayName("No discount: every allocation is zero")
        void noDiscount_allZero() {
            List<BigDecimal> result = GstTaxCalculator.allocateDiscount(
                    Arrays.asList(bd("100"), bd("200"), bd("300")), BigDecimal.ZERO);
            result.forEach(d -> assertEquals(BigDecimal.ZERO, d));
        }

        @Test
        @DisplayName("Null discount treated as zero")
        void nullDiscount_allZero() {
            List<BigDecimal> result = GstTaxCalculator.allocateDiscount(
                    Arrays.asList(bd("50"), bd("50")), null);
            result.forEach(d -> assertEquals(BigDecimal.ZERO, d));
        }

        @Test
        @DisplayName("Empty line list returns empty list")
        void emptyLines_returnsEmpty() {
            List<BigDecimal> result = GstTaxCalculator.allocateDiscount(
                    Collections.emptyList(), bd("100"));
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("3 equal lines — ₹30 discount splits evenly (₹10 each)")
        void threeEqualLines_evenSplit() {
            List<BigDecimal> result = GstTaxCalculator.allocateDiscount(
                    Arrays.asList(bd("100"), bd("100"), bd("100")), bd("30.00"));
            BigDecimal sum = result.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
            assertEquals(bd("30.00"), sum, "Total allocated must equal invoice discount");
            result.forEach(d -> assertEquals(bd("10.00"), d));
        }

        @Test
        @DisplayName("Residual paise on last item: sum == invoiceDiscount exactly")
        void residualOnLastItem_sumExact() {
            // 3 lines of ₹100 each, discount ₹10.01
            // Proportional per line = 3.33 (FLOOR) for first 2, last gets remainder
            // Line 1: floor(100/300 * 10.01) = floor(3.3366..) = 3.33
            // Line 2: same = 3.33
            // Line 3: 10.01 - 3.33 - 3.33 = 3.35
            List<BigDecimal> result = GstTaxCalculator.allocateDiscount(
                    Arrays.asList(bd("100"), bd("100"), bd("100")), bd("10.01"));
            BigDecimal sum = result.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
            assertEquals(bd("10.01"), sum, "Sum of allocations must equal invoice discount exactly");
        }

        @Test
        @DisplayName("Single line gets entire discount")
        void singleLine_getsAll() {
            List<BigDecimal> result = GstTaxCalculator.allocateDiscount(
                    Collections.singletonList(bd("500")), bd("50.00"));
            assertEquals(1, result.size());
            assertEquals(bd("50.00"), result.get(0));
        }

        @Test
        @DisplayName("Proportional split on unequal lines")
        void unequalLines_proportional() {
            // Lines: 200, 800. Discount: 100
            // Line 1: floor(200/1000 * 100) = floor(20.00) = 20.00
            // Line 2: 100 - 20 = 80.00
            List<BigDecimal> result = GstTaxCalculator.allocateDiscount(
                    Arrays.asList(bd("200"), bd("800")), bd("100.00"));
            assertEquals(bd("20.00"), result.get(0));
            assertEquals(bd("80.00"), result.get(1));
            BigDecimal sum = result.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
            assertEquals(bd("100.00"), sum);
        }

        @Test
        @DisplayName("Per-line allocation never exceeds the line's own taxable value")
        void allocationCappedAtLineValue() {
            // Line 1 = 5, Line 2 = 1000, discount = 500
            // Line 1 share would be floor(5/1005 * 500) = floor(2.48...) = 2.48 <= 5, fine
            // Last item (line 2) gets 500 - 2.48 = 497.52 < 1000, fine
            List<BigDecimal> result = GstTaxCalculator.allocateDiscount(
                    Arrays.asList(bd("5"), bd("1000")), bd("500"));
            assertTrue(result.get(0).compareTo(bd("5")) <= 0,
                    "Line 1 allocation cannot exceed its raw taxable value");
            assertTrue(result.get(1).compareTo(bd("1000")) <= 0,
                    "Line 2 allocation cannot exceed its raw taxable value");
            BigDecimal sum = result.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
            assertEquals(bd("500"), sum);
        }

        @Test
        @DisplayName("GST on proportionally discounted values is less than on gross")
        void gstReducedByProportionalDiscount() {
            // Invoice: 3 lines of ₹100 each, discount ₹30, 18% GST
            // Old (wrong): GST on 300, discount subtracted from total after tax
            //   → GST = 300 * 18% = 54.00
            // New (correct): GST on each (100 - 10) = 90
            //   → GST = 3 * 90 * 18% = 48.60
            List<BigDecimal> lines = Arrays.asList(bd("100"), bd("100"), bd("100"));
            List<BigDecimal> discounts = GstTaxCalculator.allocateDiscount(lines, bd("30"));

            BigDecimal totalGst = BigDecimal.ZERO;
            for (int i = 0; i < 3; i++) {
                BigDecimal reduced = lines.get(i).subtract(discounts.get(i));
                GstTaxCalculator.LineGst lg = GstTaxCalculator.computeLineGst(
                        reduced, GSTType.GST_18, BigDecimal.ZERO, true, false);
                totalGst = totalGst.add(lg.totalGst());
            }
            assertEquals(bd("48.60"), totalGst,
                    "GST on proportionally discounted lines must equal 48.60");
        }
    }

    // ── taxableValue ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("taxableValue")
    class TaxableValueTests {

        @Test
        void qtyTimesPrice_minusDiscount() {
            assertEquals(bd("190.00"),
                    GstTaxCalculator.taxableValue(bd("2"), bd("100.00"), bd("10.00")));
        }

        @Test
        void nullDiscount_treatedAsZero() {
            assertEquals(bd("200.00"),
                    GstTaxCalculator.taxableValue(bd("2"), bd("100.00"), null));
        }

        @Test
        void negativeResult_clampedToZero() {
            assertEquals(BigDecimal.ZERO,
                    GstTaxCalculator.taxableValue(bd("1"), bd("5.00"), bd("10.00")));
        }
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static BigDecimal bd(String val) {
        return new BigDecimal(val);
    }
}
