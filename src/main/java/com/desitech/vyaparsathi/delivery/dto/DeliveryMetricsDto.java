package com.desitech.vyaparsathi.delivery.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Rolled-up delivery operations metrics for the "how are we doing today" view.
 *
 * All values are computed over a date range from a single query pass — the
 * per-person breakdown lets managers see who is on-time vs. who has a queue
 * blowing up.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DeliveryMetricsDto {
    private LocalDate from;
    private LocalDate to;
    private long totalDeliveries;
    private long delivered;
    private long cancelled;
    private long inProgress;

    /** Delivered on or before {@code estimatedDeliveryDate}, expressed as a %. */
    private double onTimePct;

    /** Average hours from {@code createdAt} → {@code deliveredAt} for DELIVERED rows. */
    private double avgLeadTimeHours;

    /** Sum of COD amounts marked collected in the range. */
    private BigDecimal codCollectedTotal;

    /** Number of DELIVERED rows in the range with codCollected=true. */
    private long codCollectedCount;

    private List<PerPersonMetrics> perPerson;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PerPersonMetrics {
        private Long personId;
        private String personName;
        private long delivered;
        private long inProgress;
        private double onTimePct;
        private double avgLeadTimeHours;
    }
}
