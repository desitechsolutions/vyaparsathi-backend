package com.desitech.vyaparsathi.sales.enums;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Optional;

/**
 * CBIC-notified GST rate slabs, including special rates for gold/silver (3%),
 * diamonds/precious stones (0.25%), composition-equivalent levy (1.5%),
 * and the compensation cess marker for cess-liable goods.
 *
 * <p>The enum name is stored as-is in every {@code gst_type} column
 * ({@code @Enumerated(EnumType.STRING)}) — adding new constants is a
 * non-breaking schema change.
 *
 * <p><b>API contract</b>
 * <ul>
 *   <li>{@link #getRate()} — the statutory rate as a {@link BigDecimal}
 *       (authoritative for arithmetic; callers that previously used
 *       {@code BigDecimal.valueOf(gstType.getRate())} can call {@code getRate()} directly).</li>
 *   <li>{@link #getRateAsInt()} — integer truncation for legacy display/sort code that
 *       cannot yet consume BigDecimal. New code should prefer {@link #getRate()}.</li>
 *   <li>{@link #fromRate(BigDecimal)} — canonical lookup, handles all slabs including
 *       decimals; returns {@link Optional#empty()} for unknown rates so callers decide.</li>
 *   <li>{@link #fromRateOrDefault(BigDecimal)} — falls back to {@link #GST_0} with a
 *       WARN log; retained for call-sites that must not throw.</li>
 * </ul>
 */
public enum GSTType {

    // ── Standard GST slabs ───────────────────────────────────────────────────
    GST_0("0"),
    GST_5("5"),
    GST_12("12"),
    GST_18("18"),
    GST_28("28"),

    // ── Special statutory rates ──────────────────────────────────────────────
    /** 0.1% — rough diamonds and precious stones under Chapter 71, Notification 50/2017. */
    GST_0_1("0.1"),
    /** 0.25% — cut and polished diamonds (Chapter 71). */
    GST_0_25("0.25"),
    /** 1.5% — composition-scheme goods levy (CGST Rule 7, FY 2019+). */
    GST_1_5("1.5"),
    /** 3% — gold, silver, platinum, and articles thereof (Chapter 71, §4 UTGST). */
    GST_3("3"),

    // ── Compensation Cess marker ─────────────────────────────────────────────
    /**
     * Marker for lines that carry only a compensation cess component with no
     * standard GST slab (e.g. paan masala, certain coal categories).
     * The cess rate is stored separately on {@code SaleItem.cessRate}.
     * The GST rate on such lines may itself be 0%, 5% etc. — treat this value
     * as a signal that cess is present, not as the actual GST rate.
     * Use alongside the appropriate standard slab constant, not as a replacement.
     */
    GST_CESS_ONLY("0");

    // ─────────────────────────────────────────────────────────────────────────

    private static final Logger log = LoggerFactory.getLogger(GSTType.class);

    /** The statutory rate as a BigDecimal — authoritative for all arithmetic. */
    private final BigDecimal rate;

    GSTType(String rateStr) {
        this.rate = new BigDecimal(rateStr);
    }

    /**
     * Statutory GST rate as a {@link BigDecimal}.
     * Use this in all tax calculations — it is exact and requires no conversion.
     *
     * <pre>
     *   BigDecimal gst = taxable.multiply(gstType.getRate())
     *                           .divide(HUNDRED, 2, RoundingMode.HALF_UP);
     * </pre>
     */
    public BigDecimal getRate() {
        return rate;
    }

    /**
     * Integer truncation of the rate — for legacy display/sort code only.
     * <b>Do not use for arithmetic.</b> For rates like 0.25%, this returns 0.
     * Prefer {@link #getRate()} for any calculation path.
     */
    public int getRateAsInt() {
        return rate.intValue();
    }

    // ── Lookup methods ────────────────────────────────────────────────────────

    /**
     * Finds the enum constant whose rate exactly equals {@code gstRate}.
     * Returns {@link Optional#empty()} for unrecognised rates so the caller
     * can decide whether to reject, warn, or apply a custom strategy.
     *
     * @param gstRate the rate to look up; null returns empty
     */
    public static Optional<GSTType> fromRate(BigDecimal gstRate) {
        if (gstRate == null) return Optional.empty();
        BigDecimal normalised = gstRate.stripTrailingZeros();
        return Arrays.stream(values())
                .filter(t -> t.rate.stripTrailingZeros().compareTo(normalised) == 0)
                .findFirst();
    }

    /**
     * Overload accepting an {@link Integer} — delegates to {@link #fromRate(BigDecimal)}.
     * Retained so existing {@code GSTType.fromRate(itemVariant.getGstRate())} callers
     * (which pass {@code Integer}) continue to compile without changes.
     */
    public static Optional<GSTType> fromRate(Integer gstRate) {
        if (gstRate == null) return Optional.empty();
        return fromRate(new BigDecimal(gstRate));
    }

    /**
     * Convenience variant that falls back to {@link #GST_0} on unknown rates
     * instead of returning empty — matches the old silent-default behaviour.
     * <b>Use only in code paths where a hard failure is unacceptable</b>
     * (e.g. rendering a historical invoice whose rate has since been removed
     * from the enum). All creation/calculation paths should use
     * {@link #fromRate(BigDecimal)} and surface an explicit error on mismatch.
     */
    public static GSTType fromRateOrDefault(BigDecimal gstRate) {
        Optional<GSTType> found = fromRate(gstRate);
        if (found.isEmpty()) {
            log.warn("Unrecognised GST rate {}%; falling back to GST_0. "
                    + "If this is a valid rate (gold 3%, diamonds 0.25% etc.), "
                    + "add it to the GSTType enum.", gstRate);
        }
        return found.orElse(GST_0);
    }

    /** @see #fromRateOrDefault(BigDecimal) */
    public static GSTType fromRateOrDefault(Integer gstRate) {
        if (gstRate == null) {
            log.warn("GST rate is null; defaulting to GST_0.");
            return GST_0;
        }
        return fromRateOrDefault(new BigDecimal(gstRate));
    }
}
