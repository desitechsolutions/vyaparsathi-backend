package com.desitech.vyaparsathi.gst.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("GstOffsetService — Rule 88A/88B ITC Offset Engine")
class GstOffsetServiceTest {

    private GstOffsetService service;

    @BeforeEach
    void setUp() {
        service = new GstOffsetService();
    }

    // ── Rule 88A: IGST credit exhausted first ─────────────────────────────────

    @Nested
    @DisplayName("Rule 88A — IGST credit must be exhausted before CGST/SGST")
    class Rule88A {

        @Test
        @DisplayName("IGST credit offsets IGST liability first")
        void igstCredit_offsetsIgstLiabilityFirst() {
            GstOffsetService.ItcBalance itc = GstOffsetService.ItcBalance.of(
                    bd("100"), bd("50"), bd("50"), BigDecimal.ZERO, BigDecimal.ZERO);
            GstOffsetService.TaxLiability liability = GstOffsetService.TaxLiability.of(
                    bd("80"), bd("30"), bd("30"), BigDecimal.ZERO, BigDecimal.ZERO);

            GstOffsetService.OffsetResult result = service.applyOffset(itc, liability);

            // IGST credit (100) → IGST liability (80) = 80 offset, 20 IGST remaining
            assertEquals(bd("80"), result.paidThroughItc().igst(),
                    "All IGST liability should be offset by IGST credit");
            assertEquals(BigDecimal.ZERO, result.paidInCash().igst(),
                    "No cash payment for IGST since ITC covers it");
        }

        @Test
        @DisplayName("Remaining IGST credit flows to CGST after IGST exhausted")
        void remainingIgstCredit_flowsToCgst() {
            // IGST: 100 credit, 60 liability → 40 remaining IGST credit
            // CGST: 0 credit, 40 liability → should be covered by remaining IGST
            GstOffsetService.ItcBalance itc = GstOffsetService.ItcBalance.of(
                    bd("100"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
            GstOffsetService.TaxLiability liability = GstOffsetService.TaxLiability.of(
                    bd("60"), bd("40"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);

            GstOffsetService.OffsetResult result = service.applyOffset(itc, liability);

            assertEquals(BigDecimal.ZERO, result.paidInCash().igst(), "IGST cash = 0");
            assertEquals(BigDecimal.ZERO, result.paidInCash().cgst(), "CGST cash = 0 (covered by leftover IGST)");
            assertEquals(BigDecimal.ZERO, result.closingItcBalance().igst(), "All IGST ITC consumed");
        }

        @Test
        @DisplayName("CGST credit is NOT used while IGST credit remains — Rule 88A enforcement")
        void cgstCreditNotUsed_whileIgstRemains() {
            // IGST: 200 credit; CGST: 100 credit; CGST liability: 50
            // Rule 88A mandates IGST goes first — CGST credit untouched if IGST covers it
            GstOffsetService.ItcBalance itc = GstOffsetService.ItcBalance.of(
                    bd("200"), bd("100"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
            GstOffsetService.TaxLiability liability = GstOffsetService.TaxLiability.of(
                    bd("50"), bd("50"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);

            GstOffsetService.OffsetResult result = service.applyOffset(itc, liability);

            // IGST credit: 200 → covers IGST(50) and then CGST(50) = 100 used
            assertEquals(BigDecimal.ZERO, result.paidInCash().igst());
            assertEquals(BigDecimal.ZERO, result.paidInCash().cgst());
            // CGST credit should remain untouched = 100
            assertEquals(bd("100"), result.closingItcBalance().cgst(),
                    "CGST ITC should remain unused when IGST credit covered the liability");
        }

        @Test
        @DisplayName("Remaining IGST credit flows to SGST after IGST+CGST exhausted")
        void remainingIgstCredit_flowsToSgst() {
            // IGST: 150 credit, 50 IGST + 50 CGST + 50 SGST liability
            GstOffsetService.ItcBalance itc = GstOffsetService.ItcBalance.of(
                    bd("150"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
            GstOffsetService.TaxLiability liability = GstOffsetService.TaxLiability.of(
                    bd("50"), bd("50"), bd("50"), BigDecimal.ZERO, BigDecimal.ZERO);

            GstOffsetService.OffsetResult result = service.applyOffset(itc, liability);

            assertEquals(BigDecimal.ZERO, result.paidInCash().igst());
            assertEquals(BigDecimal.ZERO, result.paidInCash().cgst());
            assertEquals(BigDecimal.ZERO, result.paidInCash().sgst());
            assertEquals(BigDecimal.ZERO, result.closingItcBalance().igst());
        }
    }

    // ── Section 49A: CGST/SGST can offset same-head then IGST ────────────────

    @Nested
    @DisplayName("Section 49A — CGST/SGST own-head offset + cross to IGST")
    class Section49A {

        @Test
        @DisplayName("CGST credit offsets CGST liability, not SGST")
        void cgstCredit_notAppliedToSgst() {
            GstOffsetService.ItcBalance itc = GstOffsetService.ItcBalance.of(
                    BigDecimal.ZERO, bd("100"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
            GstOffsetService.TaxLiability liability = GstOffsetService.TaxLiability.of(
                    BigDecimal.ZERO, BigDecimal.ZERO, bd("100"), BigDecimal.ZERO, BigDecimal.ZERO);

            GstOffsetService.OffsetResult result = service.applyOffset(itc, liability);

            // CGST credit cannot offset SGST liability — must pay SGST in cash
            assertEquals(bd("100"), result.paidInCash().sgst(),
                    "SGST must be paid in cash — CGST credit cannot cross to SGST");
            assertEquals(bd("100"), result.closingItcBalance().cgst(),
                    "CGST credit remains unused");
        }

        @Test
        @DisplayName("SGST credit offsets SGST liability, not CGST")
        void sgstCredit_notAppliedToCgst() {
            GstOffsetService.ItcBalance itc = GstOffsetService.ItcBalance.of(
                    BigDecimal.ZERO, BigDecimal.ZERO, bd("100"), BigDecimal.ZERO, BigDecimal.ZERO);
            GstOffsetService.TaxLiability liability = GstOffsetService.TaxLiability.of(
                    BigDecimal.ZERO, bd("100"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);

            GstOffsetService.OffsetResult result = service.applyOffset(itc, liability);

            assertEquals(bd("100"), result.paidInCash().cgst(),
                    "CGST must be paid in cash — SGST credit cannot cross to CGST");
            assertEquals(bd("100"), result.closingItcBalance().sgst(),
                    "SGST credit remains unused");
        }

        @Test
        @DisplayName("Remaining CGST credit offsets IGST liability (Section 49B)")
        void remainingCgstCredit_offsetsIgst() {
            // No IGST credit; CGST: 80 credit covers CGST(30) + remaining 50 → IGST(100)
            GstOffsetService.ItcBalance itc = GstOffsetService.ItcBalance.of(
                    BigDecimal.ZERO, bd("80"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
            GstOffsetService.TaxLiability liability = GstOffsetService.TaxLiability.of(
                    bd("100"), bd("30"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);

            GstOffsetService.OffsetResult result = service.applyOffset(itc, liability);

            // CGST: 80 covers CGST(30) fully, 50 left → offsets IGST(100): 50 covered, 50 cash
            assertEquals(BigDecimal.ZERO, result.paidInCash().cgst(), "CGST fully covered");
            assertEquals(bd("50"), result.paidInCash().igst(), "50 IGST cash after CGST cross-head");
            assertEquals(BigDecimal.ZERO, result.closingItcBalance().cgst(), "All CGST ITC consumed");
        }

        @Test
        @DisplayName("Remaining SGST credit offsets IGST liability (Section 49B)")
        void remainingSgstCredit_offsetsIgst() {
            GstOffsetService.ItcBalance itc = GstOffsetService.ItcBalance.of(
                    BigDecimal.ZERO, BigDecimal.ZERO, bd("80"), BigDecimal.ZERO, BigDecimal.ZERO);
            GstOffsetService.TaxLiability liability = GstOffsetService.TaxLiability.of(
                    bd("100"), BigDecimal.ZERO, bd("30"), BigDecimal.ZERO, BigDecimal.ZERO);

            GstOffsetService.OffsetResult result = service.applyOffset(itc, liability);

            assertEquals(BigDecimal.ZERO, result.paidInCash().sgst());
            assertEquals(bd("50"), result.paidInCash().igst());
            assertEquals(BigDecimal.ZERO, result.closingItcBalance().sgst());
        }
    }

    // ── Cess isolation ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Cess — isolated, never crosses heads")
    class CessIsolation {

        @Test
        @DisplayName("Cess credit only offsets cess liability")
        void cessCredit_onlyCesLiability() {
            GstOffsetService.ItcBalance itc = GstOffsetService.ItcBalance.of(
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, bd("50"));
            GstOffsetService.TaxLiability liability = GstOffsetService.TaxLiability.of(
                    bd("100"), bd("100"), bd("100"), BigDecimal.ZERO, bd("30"));

            GstOffsetService.OffsetResult result = service.applyOffset(itc, liability);

            // Cess (50 ITC) offsets cess liability (30) — 20 remains
            assertEquals(BigDecimal.ZERO, result.paidInCash().cess(), "Cess liability covered by cess ITC");
            assertEquals(bd("20"), result.closingItcBalance().cess(), "20 cess ITC remaining");

            // No cross: IGST/CGST/SGST paid fully in cash
            assertEquals(bd("100"), result.paidInCash().igst());
            assertEquals(bd("100"), result.paidInCash().cgst());
            assertEquals(bd("100"), result.paidInCash().sgst());
        }

        @Test
        @DisplayName("IGST/CGST credit does not reduce cess liability")
        void gstCredit_doesNotReduceCess() {
            GstOffsetService.ItcBalance itc = GstOffsetService.ItcBalance.of(
                    bd("500"), bd("500"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
            GstOffsetService.TaxLiability liability = GstOffsetService.TaxLiability.of(
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, bd("100"));

            GstOffsetService.OffsetResult result = service.applyOffset(itc, liability);

            assertEquals(bd("100"), result.paidInCash().cess(),
                    "Cess must be paid in cash — IGST/CGST credit cannot offset cess");
        }
    }

    // ── Full integration scenarios ─────────────────────────────────────────────

    @Nested
    @DisplayName("Full offset scenarios")
    class FullScenarios {

        @Test
        @DisplayName("No ITC — all liability paid in cash")
        void noItc_allCash() {
            GstOffsetService.ItcBalance itc = GstOffsetService.ItcBalance.of(
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
            GstOffsetService.TaxLiability liability = GstOffsetService.TaxLiability.of(
                    bd("100"), bd("50"), bd("50"), BigDecimal.ZERO, bd("10"));

            GstOffsetService.OffsetResult result = service.applyOffset(itc, liability);

            assertEquals(bd("100"), result.paidInCash().igst());
            assertEquals(bd("50"),  result.paidInCash().cgst());
            assertEquals(bd("50"),  result.paidInCash().sgst());
            assertEquals(bd("10"),  result.paidInCash().cess());
        }

        @Test
        @DisplayName("No liability — all ITC carried forward as closing balance")
        void noLiability_allItcCarried() {
            GstOffsetService.ItcBalance itc = GstOffsetService.ItcBalance.of(
                    bd("100"), bd("50"), bd("50"), BigDecimal.ZERO, bd("10"));
            GstOffsetService.TaxLiability liability = GstOffsetService.TaxLiability.of(
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);

            GstOffsetService.OffsetResult result = service.applyOffset(itc, liability);

            assertEquals(BigDecimal.ZERO, result.paidInCash().igst());
            assertEquals(BigDecimal.ZERO, result.paidInCash().cgst());
            assertEquals(bd("100"), result.closingItcBalance().igst());
            assertEquals(bd("50"),  result.closingItcBalance().cgst());
            assertEquals(bd("50"),  result.closingItcBalance().sgst());
            assertEquals(bd("10"),  result.closingItcBalance().cess());
        }

        @Test
        @DisplayName("Realistic GSTR-3B scenario: mixed credits and liabilities")
        void realistic_gstr3b_scenario() {
            // Monthly scenario for a B2B supplier:
            // ITC: IGST=5000, CGST=2000, SGST=2000, cess=200
            // Outward: IGST=3000, CGST=2500, SGST=2500, cess=300
            GstOffsetService.ItcBalance itc = GstOffsetService.ItcBalance.of(
                    bd("5000"), bd("2000"), bd("2000"), BigDecimal.ZERO, bd("200"));
            GstOffsetService.TaxLiability liability = GstOffsetService.TaxLiability.of(
                    bd("3000"), bd("2500"), bd("2500"), BigDecimal.ZERO, bd("300"));

            GstOffsetService.OffsetResult result = service.applyOffset(itc, liability);

            // Step 1: IGST(5000) → IGST(3000): 3000 used, 2000 remaining
            // Step 2: IGST(2000) → CGST(2500): 2000 used, CGST(500) remaining
            // Step 3: IGST(0) → SGST: nothing
            // Step 5: CGST(2000) → CGST(500): 500 used, 1500 CGST remaining
            // Step 7: SGST(2000) → SGST(2500): 2000 used, SGST(500) remaining → cash

            assertEquals(BigDecimal.ZERO, result.paidInCash().igst());
            assertEquals(BigDecimal.ZERO, result.paidInCash().cgst());
            assertEquals(bd("500"), result.paidInCash().sgst());
            assertEquals(bd("100"), result.paidInCash().cess(), "300 cess due - 200 cess ITC = 100 cash");

            // Closing balances
            assertEquals(BigDecimal.ZERO, result.closingItcBalance().igst());
            assertEquals(bd("1500"), result.closingItcBalance().cgst());
            assertEquals(BigDecimal.ZERO, result.closingItcBalance().sgst());
        }

        @Test
        @DisplayName("Cash outflow per head never exceeds the head's original liability")
        void cashOutflow_neverExceedsLiability() {
            GstOffsetService.ItcBalance itc = GstOffsetService.ItcBalance.of(
                    bd("1000"), bd("500"), bd("500"), BigDecimal.ZERO, bd("100"));
            GstOffsetService.TaxLiability liability = GstOffsetService.TaxLiability.of(
                    bd("200"), bd("100"), bd("100"), BigDecimal.ZERO, bd("50"));

            GstOffsetService.OffsetResult result = service.applyOffset(itc, liability);

            assertTrue(result.paidInCash().igst().compareTo(bd("200")) <= 0,
                    "IGST cash cannot exceed IGST liability");
            assertTrue(result.paidInCash().cgst().compareTo(bd("100")) <= 0,
                    "CGST cash cannot exceed CGST liability");
            assertTrue(result.paidInCash().sgst().compareTo(bd("100")) <= 0,
                    "SGST cash cannot exceed SGST liability");
            assertTrue(result.paidInCash().cess().compareTo(bd("50")) <= 0,
                    "Cess cash cannot exceed cess liability");
        }

        @Test
        @DisplayName("paidThroughItc + paidInCash == original liability for each head")
        void itcPlusCash_equalsLiability() {
            GstOffsetService.ItcBalance itc = GstOffsetService.ItcBalance.of(
                    bd("300"), bd("150"), bd("150"), BigDecimal.ZERO, bd("20"));
            GstOffsetService.TaxLiability liability = GstOffsetService.TaxLiability.of(
                    bd("400"), bd("200"), bd("200"), BigDecimal.ZERO, bd("30"));

            GstOffsetService.OffsetResult result = service.applyOffset(itc, liability);

            assertAmount(bd("400"), result.paidThroughItc().igst().add(result.paidInCash().igst()), "IGST");
            assertAmount(bd("200"), result.paidThroughItc().cgst().add(result.paidInCash().cgst()), "CGST");
            assertAmount(bd("200"), result.paidThroughItc().sgst().add(result.paidInCash().sgst()), "SGST");
            assertAmount(bd("30"),  result.paidThroughItc().cess().add(result.paidInCash().cess()), "Cess");
        }

        @Test
        @DisplayName("Total closing ITC = total opening ITC - total paidThroughItc")
        void closingBalance_totalConservation() {
            // NOTE: paidThroughItc tracks how much of each LIABILITY HEAD was settled,
            // not how much of each ITC HEAD was consumed.  Per-head equalities do not
            // hold when Rule 88A cross-head offsets fire (e.g. IGST credit pays CGST
            // liability, so closing IGST ITC ≠ opening IGST - paidThroughItc.igst()).
            // The correct invariant is the total: sum(closing ITC) = sum(opening ITC)
            // - sum(paidThroughItc), because every unit of consumed ITC settles exactly
            // one unit of some liability.
            GstOffsetService.ItcBalance itc = GstOffsetService.ItcBalance.of(
                    bd("1000"), bd("500"), bd("300"), BigDecimal.ZERO, bd("100"));
            GstOffsetService.TaxLiability liability = GstOffsetService.TaxLiability.of(
                    bd("200"), bd("100"), bd("100"), BigDecimal.ZERO, bd("50"));

            GstOffsetService.OffsetResult result = service.applyOffset(itc, liability);

            BigDecimal openingTotal = bd("1000").add(bd("500")).add(bd("300")).add(bd("100"));
            BigDecimal paidTotal = result.paidThroughItc().igst()
                    .add(result.paidThroughItc().cgst())
                    .add(result.paidThroughItc().sgst())
                    .add(result.paidThroughItc().utgst())
                    .add(result.paidThroughItc().cess());
            BigDecimal closingTotal = result.closingItcBalance().igst()
                    .add(result.closingItcBalance().cgst())
                    .add(result.closingItcBalance().sgst())
                    .add(result.closingItcBalance().utgst())
                    .add(result.closingItcBalance().cess());

            assertEquals(0, openingTotal.subtract(paidTotal).compareTo(closingTotal),
                    "Total closing ITC must equal total opening ITC minus total ITC consumed");
        }
    }

    // ── UTGST scenarios ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("UTGST — Union Territory tax behaviour")
    class UtgstScenarios {

        @Test
        @DisplayName("UTGST credit offsets UTGST liability only, not SGST")
        void utgstCredit_onlyOffsetsUtgst() {
            GstOffsetService.ItcBalance itc = GstOffsetService.ItcBalance.of(
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, bd("100"), BigDecimal.ZERO);
            GstOffsetService.TaxLiability liability = GstOffsetService.TaxLiability.of(
                    BigDecimal.ZERO, BigDecimal.ZERO, bd("100"), BigDecimal.ZERO, BigDecimal.ZERO);

            GstOffsetService.OffsetResult result = service.applyOffset(itc, liability);

            assertEquals(bd("100"), result.paidInCash().sgst(),
                    "SGST cash — UTGST credit cannot offset SGST");
            assertEquals(bd("100"), result.closingItcBalance().utgst(),
                    "UTGST ITC unused");
        }

        @Test
        @DisplayName("Remaining UTGST credit offsets IGST liability")
        void remainingUtgstCredit_offsetsIgst() {
            GstOffsetService.ItcBalance itc = GstOffsetService.ItcBalance.of(
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, bd("80"), BigDecimal.ZERO);
            GstOffsetService.TaxLiability liability = GstOffsetService.TaxLiability.of(
                    bd("100"), BigDecimal.ZERO, BigDecimal.ZERO, bd("30"), BigDecimal.ZERO);

            GstOffsetService.OffsetResult result = service.applyOffset(itc, liability);

            assertEquals(BigDecimal.ZERO, result.paidInCash().utgst());
            assertEquals(bd("50"), result.paidInCash().igst(),
                    "50 IGST cash after UTGST cross-head offset");
        }
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static BigDecimal bd(String val) {
        return new BigDecimal(val);
    }

    private static void assertAmount(BigDecimal expected, BigDecimal actual, String head) {
        assertEquals(0, expected.compareTo(actual),
                String.format("%s expected %s but was %s", head, expected, actual));
    }
}
