package com.desitech.vyaparsathi.analytics.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * Immutable value object for every date-range–scoped analytics query.
 *
 * The previous analytics package hard-coded lookback windows inside the
 * service ({@code LocalDate.now().minusMonths(2)} etc.), which meant the
 * dashboard could never be scoped to a custom period. Every enterprise-grade
 * KPI/time-series/segment endpoint now accepts an {@link AnalyticsRange}
 * so the client controls the window and the "compare with previous"
 * lookup is a first-class operation.
 *
 * Invariants:
 *   • {@code from} and {@code to} are inclusive dates (00:00 → 23:59:59.999999999).
 *   • {@code to} is never before {@code from}.
 *   • {@link #previousPeriod()} shifts the range back by its own length, so
 *     comparing "last 7 days" to "the 7 days before that" is one call.
 */
public final class AnalyticsRange {

    public enum Granularity {
        DAY,
        WEEK,
        MONTH;

        public static Granularity fromString(String s) {
            if (s == null || s.isBlank()) return DAY;
            try {
                return Granularity.valueOf(s.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                return DAY;
            }
        }
    }

    private final LocalDate from;
    private final LocalDate to;
    private final Granularity granularity;

    private AnalyticsRange(LocalDate from, LocalDate to, Granularity granularity) {
        if (from == null) from = LocalDate.now().minusDays(29);
        if (to == null) to = LocalDate.now();
        if (to.isBefore(from)) {
            // Silently swap rather than throw — a UI date-picker glitch shouldn't 500.
            LocalDate swap = from; from = to; to = swap;
        }
        this.from = from;
        this.to = to;
        this.granularity = granularity == null ? Granularity.DAY : granularity;
    }

    public static AnalyticsRange of(LocalDate from, LocalDate to) {
        return new AnalyticsRange(from, to, Granularity.DAY);
    }

    public static AnalyticsRange of(LocalDate from, LocalDate to, Granularity g) {
        return new AnalyticsRange(from, to, g);
    }

    /** Default: last 30 days ending today. */
    public static AnalyticsRange last30Days() {
        LocalDate today = LocalDate.now();
        return new AnalyticsRange(today.minusDays(29), today, Granularity.DAY);
    }

    /** Length of this range in days (inclusive of both endpoints). */
    public long lengthDays() {
        return ChronoUnit.DAYS.between(from, to) + 1;
    }

    /**
     * Return the immediately preceding period of equal length. Used for
     * period-over-period comparisons — "this week vs previous week", etc.
     */
    public AnalyticsRange previousPeriod() {
        long len = lengthDays();
        LocalDate prevTo = from.minusDays(1);
        LocalDate prevFrom = prevTo.minusDays(len - 1);
        return new AnalyticsRange(prevFrom, prevTo, granularity);
    }

    public LocalDate getFrom() { return from; }
    public LocalDate getTo() { return to; }
    public Granularity getGranularity() { return granularity; }

    /** Start-of-day for use in {@code date >= :start} predicates on LocalDateTime columns. */
    public LocalDateTime startInclusive() { return from.atStartOfDay(); }

    /** End-of-day for use in {@code date <= :end} predicates on LocalDateTime columns. */
    public LocalDateTime endInclusive() {
        return to.atTime(23, 59, 59, 999_999_999);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AnalyticsRange other)) return false;
        return from.equals(other.from) && to.equals(other.to) && granularity == other.granularity;
    }

    @Override
    public int hashCode() {
        return Objects.hash(from, to, granularity);
    }

    /** Stable, human-readable form — used as part of cache keys. */
    @Override
    public String toString() {
        return "AnalyticsRange[" + from + ".." + to + "/" + granularity + "]";
    }
}
