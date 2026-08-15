package com.desitech.vyaparsathi.common.util;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;

/**
 * Indian-locale currency + number formatter. Prints 1,23,45,678.90 rather than
 * 12,345,678.90 — the lakh/crore grouping every enterprise Indian tax
 * document ships with.
 *
 * <p>Two shapes:
 * <ul>
 *   <li>{@link #formatCurrency(BigDecimal)} — with ₹ prefix, always 2 decimals
 *   <li>{@link #formatNumber(BigDecimal)}  — bare number, 2 decimals
 *   <li>{@link #formatQty(BigDecimal)}     — bare number, no forced decimals
 * </ul>
 */
public final class IndianCurrencyFormat {

    private static final Locale INDIA = new Locale("en", "IN");

    private static final ThreadLocal<NumberFormat> CURRENCY = ThreadLocal.withInitial(() -> {
        NumberFormat nf = NumberFormat.getCurrencyInstance(INDIA);
        nf.setMinimumFractionDigits(2);
        nf.setMaximumFractionDigits(2);
        return nf;
    });

    private static final ThreadLocal<NumberFormat> NUMBER = ThreadLocal.withInitial(() -> {
        NumberFormat nf = NumberFormat.getInstance(INDIA);
        nf.setMinimumFractionDigits(2);
        nf.setMaximumFractionDigits(2);
        nf.setGroupingUsed(true);
        return nf;
    });

    private static final ThreadLocal<NumberFormat> QTY = ThreadLocal.withInitial(() -> {
        NumberFormat nf = NumberFormat.getInstance(INDIA);
        nf.setMinimumFractionDigits(0);
        nf.setMaximumFractionDigits(3);
        nf.setGroupingUsed(true);
        return nf;
    });

    private IndianCurrencyFormat() {}

    /** Returns "₹1,23,456.78" — with rupee symbol. Null → "₹0.00". */
    public static String formatCurrency(BigDecimal value) {
        BigDecimal safe = value != null ? value : BigDecimal.ZERO;
        // JDK's currency formatter includes a non-breaking space (₹ 1,234.00).
        // Enterprise docs prefer tight prefix — strip the NBSP.
        return CURRENCY.get().format(safe).replace(" ", "");
    }

    /** Same as {@link #formatCurrency} but without the ₹ prefix — for tabular columns. */
    public static String formatNumber(BigDecimal value) {
        BigDecimal safe = value != null ? value : BigDecimal.ZERO;
        return NUMBER.get().format(safe);
    }

    /** Quantity formatter — no forced decimals, up to 3 if fractional. */
    public static String formatQty(BigDecimal value) {
        BigDecimal safe = value != null ? value : BigDecimal.ZERO;
        return QTY.get().format(safe);
    }

    public static String formatQty(int value) {
        return QTY.get().format(value);
    }

    /** Common helper: "18%" (accepts BigDecimal 18 or 18.00). */
    public static String formatPercent(BigDecimal ratePct) {
        if (ratePct == null) return "—";
        return ratePct.stripTrailingZeros().toPlainString() + "%";
    }
}
