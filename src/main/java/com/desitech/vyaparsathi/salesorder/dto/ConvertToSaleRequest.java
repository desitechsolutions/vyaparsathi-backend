package com.desitech.vyaparsathi.salesorder.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * Body for {@code POST /api/sales-orders/{id}/convert-to-sale}.
 * Each line specifies which SO item to fulfill and how much of it. Empty list
 * = fulfill all remaining qty on all lines (full conversion).
 */
@Data
public class ConvertToSaleRequest {
    private List<LineFulfillment> lines;

    @Data
    public static class LineFulfillment {
        private Long salesOrderItemId;
        private BigDecimal qty;
    }
}
