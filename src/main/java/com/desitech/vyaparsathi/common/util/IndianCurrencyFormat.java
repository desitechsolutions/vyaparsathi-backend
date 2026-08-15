package com.desitech.vyaparsathi.common.util;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;

/**
 * Indian-locale currency + number formatter. Prints 1,23,45,678.90 rather than
 * 12,345,678.90 — the lakh/crore grouping every enterprise Indian tax
 * document ships with.
 *
 * <p>We deliberately avoid {@link NumberFormat#getCurrencyInstance} because
 * it prefixes the ₹ symbol (U+20B9), which OpenPDF's built-in Helvetica
 * cannot render (comes out blank). Every callsite uses "INR " (ISO 4217)
 * so values render in any PDF font.
 */
public final class IndianCurrencyFormat {

    private static final Locale INDIA = new Locale("en", "IN");

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

    /** Returns "INR 1,23,456.78" — ISO 4217 prefix so it renders in every
     *  PDF font. Null → "INR 0.00". */
    public static String formatCurrency(BigDecimal value) {
        BigDecimal safe = value != null ? value : BigDecimal.ZERO;
        return "INR " + NUMBER.get().format(safe);
    }

    /** Same as {@link #formatCurrency} but without the INR prefix — for
     *  tabular columns whose header already carries the currency label. */
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
