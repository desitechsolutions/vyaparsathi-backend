package com.desitech.vyaparsathi.gst.service;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * Stateless Rule 88A / 88B ITC offset engine for GSTR-3B Table 6.
 *
 * <h3>Statutory basis</h3>
 * <ul>
 *   <li><b>Rule 88A</b> — IGST credit must be fully utilised before CGST or SGST
 *       credits are applied. The mandatory sequence is:
 *       IGST→IGST, then IGST→CGST/SGST optimally.</li>
 *   <li><b>Section 49A/49B</b> — CGST credit may offset CGST and then IGST
 *       (never SGST). SGST/UTGST credit may offset SGST/UTGST and then IGST
 *       (never CGST). Cross-head CGST↔SGST offset is prohibited.</li>
 *   <li><b>Cess</b> — may only offset cess liability; cannot cross to any other head.</li>
 * </ul>
 *
 * <h3>Offset order implemented (10 steps)</h3>
 * <pre>
 *   1. IGST credit  → IGST liability
 *   2. IGST credit  → CGST liability   (Rule 88A)
 *   3. IGST credit  → SGST liability   (Rule 88A)
 *   4. IGST credit  → UTGST liability  (Rule 88A)
 *   5. CGST credit  → CGST liability
 *   6. CGST credit  → IGST liability   (Section 49B)
 *   7. SGST credit  → SGST liability
 *   8. SGST credit  → IGST liability   (Section 49B)
 *   9. UTGST credit → UTGST liability
 *  10. UTGST credit → IGST liability   (Section 49B)
 *  11. Cess credit  → Cess liability only
 * </pre>
 *
 * After all ITC is exhausted the remaining liability in each head is the
 * cash outflow required via PMT-06 electronic cash ledger.
 */
@Service
public class GstOffsetService {

    // ── Input records ─────────────────────────────────────────────────────────

    /**
     * Available ITC opening balances for the GSTR-3B period, by head.
     * All values must be non-negative. Pass {@link BigDecimal#ZERO} when a head
     * has no ITC (do not pass null).
     */
    public record ItcBalance(
            BigDecimal igst,
            BigDecimal cgst,
            BigDecimal sgst,
            BigDecimal utgst,
            BigDecimal cess
    ) {
        public static ItcBalance of(BigDecimal igst, BigDecimal cgst,
                                    BigDecimal sgst, BigDecimal utgst, BigDecimal cess) {
            return new ItcBalance(safe(igst), safe(cgst), safe(sgst), safe(utgst), safe(cess));
        }
    }

    /** Total GST liability for the GSTR-3B period, by head. */
    public record TaxLiability(
            BigDecimal igst,
            BigDecimal cgst,
            BigDecimal sgst,
            BigDecimal utgst,
            BigDecimal cess
    ) {
        public static TaxLiability of(BigDecimal igst, BigDecimal cgst,
                                      BigDecimal sgst, BigDecimal utgst, BigDecimal cess) {
            return new TaxLiability(safe(igst), safe(cgst), safe(sgst), safe(utgst), safe(cess));
        }
    }

    // ── Output record ─────────────────────────────────────────────────────────

    /**
     * Result of the Rule 88A/88B offset computation.
     *
     * <ul>
     *   <li>{@code paidThroughItc} — how much of each head's liability was settled
     *       by ITC (possibly from a different ITC head per cross-head rules).</li>
     *   <li>{@code paidInCash} — remaining liability after ITC exhaustion; this is
     *       the amount the taxpayer must pay in cash via the Electronic Cash Ledger.</li>
     *   <li>{@code closingItcBalance} — unused ITC carried forward to the next period.</li>
     * </ul>
     */
    public record OffsetResult(
            TaxComponents paidThroughItc,
            TaxComponents paidInCash,
            TaxComponents closingItcBalance
    ) {}

    /** A bundle of tax-head amounts (igst, cgst, sgst, utgst, cess). */
    public record TaxComponents(
            BigDecimal igst,
            BigDecimal cgst,
            BigDecimal sgst,
            BigDecimal utgst,
            BigDecimal cess
    ) {
        public static TaxComponents of(BigDecimal igst, BigDecimal cgst,
                                       BigDecimal sgst, BigDecimal utgst, BigDecimal cess) {
            return new TaxComponents(safe(igst), safe(cgst), safe(sgst), safe(utgst), safe(cess));
        }

        public static TaxComponents zero() {
            return new TaxComponents(BigDecimal.ZERO, BigDecimal.ZERO,
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        }
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Applies Rule 88A/88B ITC offset in the mandatory GSTN 11-step sequence.
     *
     * @param itc       available ITC opening balances by head (non-negative)
     * @param liability total tax liability by head for the period (non-negative)
     * @return {@link OffsetResult} with per-head paid-through-ITC, paid-in-cash,
     *         and closing ITC balance
     */
    public OffsetResult applyOffset(ItcBalance itc, TaxLiability liability) {

        // Mutable working copies — amounts "consumed" during each step
        BigDecimal rItcIgst   = safe(itc.igst());
        BigDecimal rItcCgst   = safe(itc.cgst());
        BigDecimal rItcSgst   = safe(itc.sgst());
        BigDecimal rItcUtgst  = safe(itc.utgst());
        BigDecimal rItcCess   = safe(itc.cess());

        BigDecimal rLiabIgst  = safe(liability.igst());
        BigDecimal rLiabCgst  = safe(liability.cgst());
        BigDecimal rLiabSgst  = safe(liability.sgst());
        BigDecimal rLiabUtgst = safe(liability.utgst());
        BigDecimal rLiabCess  = safe(liability.cess());

        // Accumulators: how much each liability head was paid through ITC
        BigDecimal pItcIgst  = BigDecimal.ZERO;
        BigDecimal pItcCgst  = BigDecimal.ZERO;
        BigDecimal pItcSgst  = BigDecimal.ZERO;
        BigDecimal pItcUtgst = BigDecimal.ZERO;
        BigDecimal pItcCess  = BigDecimal.ZERO;

        BigDecimal used;

        // ── Step 1: IGST credit → IGST liability ──────────────────────────────
        used      = min(rItcIgst, rLiabIgst);
        pItcIgst  = pItcIgst.add(used);
        rItcIgst  = rItcIgst.subtract(used);
        rLiabIgst = rLiabIgst.subtract(used);

        // ── Step 2: IGST credit → CGST liability (Rule 88A) ──────────────────
        used      = min(rItcIgst, rLiabCgst);
        pItcCgst  = pItcCgst.add(used);
        rItcIgst  = rItcIgst.subtract(used);
        rLiabCgst = rLiabCgst.subtract(used);

        // ── Step 3: IGST credit → SGST liability (Rule 88A) ──────────────────
        used      = min(rItcIgst, rLiabSgst);
        pItcSgst  = pItcSgst.add(used);
        rItcIgst  = rItcIgst.subtract(used);
        rLiabSgst = rLiabSgst.subtract(used);

        // ── Step 4: IGST credit → UTGST liability (Rule 88A) ─────────────────
        used       = min(rItcIgst, rLiabUtgst);
        pItcUtgst  = pItcUtgst.add(used);
        rItcIgst   = rItcIgst.subtract(used);
        rLiabUtgst = rLiabUtgst.subtract(used);
        // Any remaining IGST credit becomes closing balance after step 4

        // ── Step 5: CGST credit → CGST liability (Section 49A) ───────────────
        used      = min(rItcCgst, rLiabCgst);
        pItcCgst  = pItcCgst.add(used);
        rItcCgst  = rItcCgst.subtract(used);
        rLiabCgst = rLiabCgst.subtract(used);

        // ── Step 6: Remaining CGST credit → IGST liability (Section 49B) ─────
        // CGST credit CAN offset IGST; it CANNOT offset SGST/UTGST.
        used      = min(rItcCgst, rLiabIgst);
        pItcIgst  = pItcIgst.add(used);
        rItcCgst  = rItcCgst.subtract(used);
        rLiabIgst = rLiabIgst.subtract(used);

        // ── Step 7: SGST credit → SGST liability (Section 49A) ───────────────
        used      = min(rItcSgst, rLiabSgst);
        pItcSgst  = pItcSgst.add(used);
        rItcSgst  = rItcSgst.subtract(used);
        rLiabSgst = rLiabSgst.subtract(used);

        // ── Step 8: Remaining SGST credit → IGST liability (Section 49B) ─────
        used      = min(rItcSgst, rLiabIgst);
        pItcIgst  = pItcIgst.add(used);
        rItcSgst  = rItcSgst.subtract(used);
        rLiabIgst = rLiabIgst.subtract(used);

        // ── Step 9: UTGST credit → UTGST liability (Section 49A) ─────────────
        used       = min(rItcUtgst, rLiabUtgst);
        pItcUtgst  = pItcUtgst.add(used);
        rItcUtgst  = rItcUtgst.subtract(used);
        rLiabUtgst = rLiabUtgst.subtract(used);

        // ── Step 10: Remaining UTGST credit → IGST liability (Section 49B) ───
        used      = min(rItcUtgst, rLiabIgst);
        pItcIgst  = pItcIgst.add(used);
        rItcUtgst = rItcUtgst.subtract(used);
        rLiabIgst = rLiabIgst.subtract(used);

        // ── Step 11: Cess credit → Cess liability only ────────────────────────
        used      = min(rItcCess, rLiabCess);
        pItcCess  = pItcCess.add(used);
        rItcCess  = rItcCess.subtract(used);
        rLiabCess = rLiabCess.subtract(used);

        // ── Assemble result ───────────────────────────────────────────────────
        TaxComponents paidThroughItc   = TaxComponents.of(pItcIgst, pItcCgst, pItcSgst, pItcUtgst, pItcCess);
        TaxComponents paidInCash       = TaxComponents.of(rLiabIgst, rLiabCgst, rLiabSgst, rLiabUtgst, rLiabCess);
        TaxComponents closingItcBal    = TaxComponents.of(rItcIgst, rItcCgst, rItcSgst, rItcUtgst, rItcCess);

        return new OffsetResult(paidThroughItc, paidInCash, closingItcBal);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static BigDecimal min(BigDecimal a, BigDecimal b) {
        return a.min(b);
    }

    private static BigDecimal safe(BigDecimal v) {
        return v != null ? v.max(BigDecimal.ZERO) : BigDecimal.ZERO;
    }
}
