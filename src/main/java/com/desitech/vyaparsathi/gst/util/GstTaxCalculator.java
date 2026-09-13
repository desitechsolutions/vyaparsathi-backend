package com.desitech.vyaparsathi.gst.util;

import com.desitech.vyaparsathi.gst.enums.LineType;
import com.desitech.vyaparsathi.sales.enums.GSTType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Stateless arithmetic primitives for GST line-item calculation.
 *
 * <h3>Design principles</h3>
 * <ol>
 *   <li>Every method is pure — no side-effects, no Spring bean, fully unit-testable.</li>
 *   <li>All arithmetic uses {@link BigDecimal} with {@link RoundingMode#HALF_UP} and
 *       explicit scale, matching CBIC's prescribed rounding for tax computation.</li>
 *   <li>The CGST/SGST half-split uses a floor-then-complement pattern to guarantee
 *       {@code cgst + sgst = totalGst} to the paise — eliminating the off-by-one
 *       gap that appeared when {@code totalGst} had an odd-paise value (e.g. ₹0.01).</li>
 *   <li>Cess is treated as a separate component — never folded into CGST/SGST/IGST.</li>
 * </ol>
 *
 * <h3>Usage example (SaleService)</h3>
 * <pre>{@code
 *   GstTaxCalculator.LineGst result = GstTaxCalculator.computeLineGst(
 *       taxableValue, gstType, cessRate, isIntraState, isUnionTerritory
 *   );
 *   saleItem.setCgstAmt(result.cgst());
 *   saleItem.setSgstAmt(result.sgst());
 *   saleItem.setUtgstAmt(result.utgst());
 *   saleItem.setIgstAmt(result.igst());
 *   saleItem.setCessAmt(result.cess());
 * }</pre>
 */
public final class GstTaxCalculator {

    // ── Constants ─────────────────────────────────────────────────────────────

    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final BigDecimal TWO     = new BigDecimal("2");

    /** Scale used for all intermediate and final tax amounts (paise precision). */
    private static final int TAX_SCALE = 2;

    private GstTaxCalculator() { /* utility class — no instances */ }

    // ── Core record ──────────────────────────────────────────────────────────

    /**
     * Holds the fully computed GST components for one transaction line.
     * Exactly one of {cgst+sgst, cgst+utgst, igst} will be non-zero per line;
     * the others are always {@link BigDecimal#ZERO}.
     */
    public record LineGst(
            BigDecimal cgst,
            BigDecimal sgst,
            BigDecimal utgst,
            BigDecimal igst,
            BigDecimal cess
    ) {
        /** Total GST (cgst + sgst + igst + utgst). Does NOT include cess. */
        public BigDecimal totalGst() {
            return cgst.add(sgst).add(utgst).add(igst);
        }

        /** Total GST inclusive of compensation cess. */
        public BigDecimal totalTax() {
            return totalGst().add(cess);
        }

        /** Line total = taxableValue + totalTax(). Caller adds taxableValue. */
        public BigDecimal lineAmount(BigDecimal taxableValue) {
            return taxableValue.add(totalTax());
        }
    }

    // ── Primary computation method ────────────────────────────────────────────

    /**
     * Computes all GST components for a single transaction line.
     *
     * <h4>CGST/SGST split algorithm (CA-4 fix)</h4>
     * For intra-state supplies, GST must be split 50:50 between CGST and SGST/UTGST.
     * When the totalGst has an odd-paise value (e.g. ₹0.01) a naïve half-and-round
     * produces cgst = sgst = ₹0.01, giving a total of ₹0.02 — one paise more than
     * the correct total. The correct approach:
     * <pre>
     *   cgst = floor(totalGst / 2)  → always rounds DOWN
     *   sgst = totalGst − cgst       → absorbs the residual paise, ensuring exact equality
     * </pre>
     * This guarantees: cgst + sgst == totalGst, always.
     *
     * @param taxableValue   line taxable value (qty × unitPrice − discount), already scaled
     * @param gstType        GST rate slab from the enum; null or GST_0 = no GST
     * @param cessRate       compensation cess rate %; null or zero = no cess
     * @param isIntraState   true when shop state == customer/supplier state (CGST+SGST apply)
     * @param isUnionTerritory true when the shop is in a Union Territory (CGST+UTGST, not SGST)
     * @return fully computed {@link LineGst} record, never null; all fields are non-null BigDecimals
     */
    public static LineGst computeLineGst(
            BigDecimal taxableValue,
            GSTType    gstType,
            BigDecimal cessRate,
            boolean    isIntraState,
            boolean    isUnionTerritory
    ) {
        if (taxableValue == null || taxableValue.signum() == 0) {
            return zeroGst();
        }

        BigDecimal tv = taxableValue.setScale(TAX_SCALE, RoundingMode.HALF_UP);

        // ── Standard GST ────────────────────────────────────────────────────
        BigDecimal totalGst = BigDecimal.ZERO;
        if (gstType != null && gstType.getRate().signum() > 0) {
            totalGst = tv.multiply(gstType.getRate())
                         .divide(HUNDRED, TAX_SCALE, RoundingMode.HALF_UP);
        }

        // ── Compensation Cess ────────────────────────────────────────────────
        BigDecimal cessAmount = BigDecimal.ZERO;
        if (cessRate != null && cessRate.signum() > 0) {
            cessAmount = tv.multiply(cessRate)
                           .divide(HUNDRED, TAX_SCALE, RoundingMode.HALF_UP);
        }

        // ── Route GST to correct components ─────────────────────────────────
        if (totalGst.signum() == 0) {
            // Nil-rated / exempt / zero-rated: all GST components are zero; cess may still apply.
            return new LineGst(BigDecimal.ZERO, BigDecimal.ZERO,
                               BigDecimal.ZERO, BigDecimal.ZERO, cessAmount);
        }

        if (isIntraState) {
            // Floor the first half DOWN so the complement absorbs any residual paise.
            // This guarantees cgst + (sgst|utgst) == totalGst exactly.
            BigDecimal cgst = totalGst.divide(TWO, TAX_SCALE, RoundingMode.FLOOR);
            BigDecimal secondHalf = totalGst.subtract(cgst); // complement — always exact

            if (isUnionTerritory) {
                // UT: CGST + UTGST (not SGST)
                return new LineGst(cgst, BigDecimal.ZERO, secondHalf, BigDecimal.ZERO, cessAmount);
            } else {
                // Regular state: CGST + SGST
                return new LineGst(cgst, secondHalf, BigDecimal.ZERO, BigDecimal.ZERO, cessAmount);
            }
        } else {
            // Inter-state / export / SEZ: IGST only
            return new LineGst(BigDecimal.ZERO, BigDecimal.ZERO,
                               BigDecimal.ZERO, totalGst, cessAmount);
        }
    }

    /**
     * Overload for service lines where no cess applies.
     * Delegates to {@link #computeLineGst(BigDecimal, GSTType, BigDecimal, boolean, boolean)}
     * with {@code cessRate = ZERO}.
     */
    public static LineGst computeLineGst(
            BigDecimal taxableValue,
            GSTType    gstType,
            boolean    isIntraState,
            boolean    isUnionTerritory
    ) {
        return computeLineGst(taxableValue, gstType, BigDecimal.ZERO, isIntraState, isUnionTerritory);
    }

    // ── Taxable value ─────────────────────────────────────────────────────────

    /**
     * Computes the taxable value for a line item.
     * Formula: max(0, qty × unitPrice − lineDiscount).
     *
     * <p>The line discount is a flat-rupee amount, not a percentage — percentage
     * discounts must be resolved by the caller before invoking this method.
     *
     * @param qty          quantity, positive
     * @param unitPrice    unit price, positive
     * @param lineDiscount flat discount on this line, zero or positive
     * @return taxable value, never negative, scaled to 2 decimal places
     */
    public static BigDecimal taxableValue(BigDecimal qty, BigDecimal unitPrice, BigDecimal lineDiscount) {
        if (qty == null || unitPrice == null) return BigDecimal.ZERO;
        BigDecimal gross = qty.multiply(unitPrice).setScale(TAX_SCALE, RoundingMode.HALF_UP);
        BigDecimal discount = (lineDiscount != null) ? lineDiscount.abs() : BigDecimal.ZERO;
        BigDecimal net = gross.subtract(discount);
        return net.signum() < 0 ? BigDecimal.ZERO : net.setScale(TAX_SCALE, RoundingMode.HALF_UP);
    }

    // ── Bill-level discount allocation ───────────────────────────────────────

    /**
     * Allocates a bill-level invoice discount proportionally across line items,
     * per Section 15(3)(b) CGST Act: discount must reduce the taxable value of
     * each line in proportion to its share of the total before GST is computed.
     *
     * <h4>Algorithm</h4>
     * <ul>
     *   <li>Each line gets {@code floor(lineRaw / totalRaw × discount)} — FLOOR
     *       prevents the sum exceeding the discount.</li>
     *   <li>The last line absorbs the residual paise, guaranteeing
     *       {@code sum(result) == invoiceDiscount} exactly.</li>
     *   <li>Each allocation is capped at the line's own raw taxable value so no
     *       line ever goes negative.</li>
     * </ul>
     *
     * @param rawTaxables     per-line taxable values before any bill-level discount
     * @param invoiceDiscount total bill-level discount to distribute (non-negative)
     * @return list of per-line discount allocations; same size as {@code rawTaxables}
     */
    public static List<BigDecimal> allocateDiscount(List<BigDecimal> rawTaxables,
                                                    BigDecimal invoiceDiscount) {
        List<BigDecimal> result = new ArrayList<>(rawTaxables.size());
        if (rawTaxables.isEmpty()) return result;

        // Fast-path: no discount
        if (invoiceDiscount == null || invoiceDiscount.signum() <= 0) {
            rawTaxables.forEach(t -> result.add(BigDecimal.ZERO));
            return result;
        }

        BigDecimal totalRaw = rawTaxables.stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (totalRaw.signum() == 0) {
            rawTaxables.forEach(t -> result.add(BigDecimal.ZERO));
            return result;
        }

        BigDecimal allocated = BigDecimal.ZERO;
        int last = rawTaxables.size() - 1;
        for (int i = 0; i <= last; i++) {
            BigDecimal raw = rawTaxables.get(i);
            BigDecimal share;
            if (i == last) {
                // Residual goes to the last item; cap so it can't exceed the line value
                share = invoiceDiscount.subtract(allocated)
                        .min(raw)
                        .max(BigDecimal.ZERO);
            } else {
                // Proportional share, floored to avoid overshooting
                share = raw.multiply(invoiceDiscount)
                           .divide(totalRaw, TAX_SCALE, RoundingMode.FLOOR)
                           .min(raw); // cap at line value
                allocated = allocated.add(share);
            }
            result.add(share);
        }
        return result;
    }

    // ── Grand total ───────────────────────────────────────────────────────────

    /**
     * Rounds a grand total to the nearest rupee and returns the round-off component.
     *
     * @param grandTotal the pre-round grand total
     * @return a two-element array: [roundedTotal, roundOff] where
     *         roundOff = roundedTotal − grandTotal (signed, may be negative)
     */
    public static BigDecimal[] roundGrandTotal(BigDecimal grandTotal) {
        BigDecimal rounded = grandTotal.setScale(0, RoundingMode.HALF_UP);
        BigDecimal roundOff = rounded.subtract(grandTotal).setScale(TAX_SCALE, RoundingMode.HALF_UP);
        return new BigDecimal[]{rounded, roundOff};
    }

    // ── UQC helper ────────────────────────────────────────────────────────────

    /**
     * Returns the GSTN-compliant UQC string for a line.
     * Service lines must report {@code "NA"} regardless of the stored UQC value.
     */
    public static String gstnUqc(String storedUqc, LineType lineType) {
        if (lineType == LineType.SERVICES) return "NA";
        return (storedUqc != null && !storedUqc.isBlank()) ? storedUqc.trim().toUpperCase() : "OTH";
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    private static LineGst zeroGst() {
        return new LineGst(BigDecimal.ZERO, BigDecimal.ZERO,
                           BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
    }
}
