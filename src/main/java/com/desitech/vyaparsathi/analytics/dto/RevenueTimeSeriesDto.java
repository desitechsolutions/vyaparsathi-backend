package com.desitech.vyaparsathi.analytics.dto;

import com.desitech.vyaparsathi.analytics.model.AnalyticsRange;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

/**
 * Revenue and sale count over a range, bucketed by day / week / month.
 * Buckets are dense — every period between {@code from} and {@code to} appears,
 * even if it has zero revenue, so charts don't leave gaps.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RevenueTimeSeriesDto {
    private LocalDate from;
    private LocalDate to;
    private AnalyticsRange.Granularity granularity;
    private List<RevenueBucketDto> buckets;
}
