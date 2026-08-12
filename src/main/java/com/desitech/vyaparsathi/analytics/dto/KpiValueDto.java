package com.desitech.vyaparsathi.analytics.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Single KPI with period-over-period comparison.
 *
 * {@code current} — value for the requested range.
 * {@code previous} — value for the immediately preceding, equal-length range.
 * {@code changePct} — signed percentage change (may be null when previous is zero and
 * current is also zero; when previous is zero but current is not, we report 100.0
 * to represent "new revenue where there was none").
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class KpiValueDto {
    private BigDecimal current;
    private BigDecimal previous;
    private Double changePct;

    public static KpiValueDto of(BigDecimal current, BigDecimal previous) {
        BigDecimal cur = current == null ? BigDecimal.ZERO : current;
        BigDecimal prev = previous == null ? BigDecimal.ZERO : previous;
        Double pct;
        if (prev.signum() == 0) {
            pct = (cur.signum() == 0) ? null : 100.0;
        } else {
            pct = cur.subtract(prev)
                    .divide(prev.abs(), 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .doubleValue();
        }
        return new KpiValueDto(cur, prev, pct);
    }

    public static KpiValueDto ofLong(long current, long previous) {
        return of(BigDecimal.valueOf(current), BigDecimal.valueOf(previous));
    }
}
