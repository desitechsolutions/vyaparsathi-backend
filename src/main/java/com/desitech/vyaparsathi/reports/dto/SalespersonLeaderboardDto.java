package com.desitech.vyaparsathi.reports.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * One row of the salesperson leaderboard — a single user's aggregate over
 * the query window. Ranked by {@code totalSales} descending in the service.
 * Sales with a null {@code salespersonId} are not counted toward anyone.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SalespersonLeaderboardDto {
    /** Rank position within the returned list, 1-indexed. */
    private int rank;

    private Long salespersonId;

    /** Best-effort display name: firstName lastName, else username, else "User #{id}". */
    private String salespersonName;

    /** Sum of {@code Sale.grandTotal} over the window. */
    private BigDecimal totalSales;

    /** Number of sales attributed to this salesperson. */
    private long saleCount;

    /** {@code totalSales / saleCount} rounded to 2 decimals; 0 when saleCount = 0. */
    private BigDecimal avgSaleValue;
}
