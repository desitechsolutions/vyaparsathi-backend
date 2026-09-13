package com.desitech.vyaparsathi.sales.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * Request DTO for queuing an offline sale
 * Received from frontend and stored for async processing
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OfflineSalesQueueRequest {

    /**
     * Client-generated transaction ID (UUID)
     * Used for idempotency and duplicate detection
     */
    private String clientTxnId;

    /**
     * Device ID from frontend (for tracking multi-device scenarios)
     */
    private String deviceId;

    // ═══════════════════════════════════════════════════════════════
    // CORE SALE DATA
    // ═══════════════════════════════════════════════════════════════

    private Long customerId;
    private String customerName;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SaleItemDto {
        private Long variantId;
        private Integer qty;
        private BigDecimal unitPrice;
        private BigDecimal discount;

        // Custom / service line items (variantId is null for these)
        private Boolean isCustom;
        private String customItemName;
        private String customDescription;
        private String customHsnSac;
        private String customUnit;
        private BigDecimal gstRate; // explicit rate for custom items
    }

    private List<SaleItemDto> items;
    private BigDecimal totalAmount;
    private BigDecimal discount;

    // ═══════════════════════════════════════════════════════════════
    // GST & STATUTORY
    // ═══════════════════════════════════════════════════════════════

    private String isGstRequired; // "yes", "no", "exempt"
    private String placeOfSupply;
    private String supplyType;
    private Boolean reverseCharge;
    private String billToAddress;
    private String shipToAddress;
    private String consigneeAddress;

    // ═══════════════════════════════════════════════════════════════
    // PAYMENT
    // ═══════════════════════════════════════════════════════════════

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PaymentMethodDto {
        private String method; // Cash, Card, UPI, Cheque, etc.
        private BigDecimal amount;
    }

    private List<PaymentMethodDto> paymentMethods;

    // ═══════════════════════════════════════════════════════════════
    // DELIVERY
    // ═══════════════════════════════════════════════════════════════

    private Boolean deliveryRequired;
    private String deliveryAddress;
    private BigDecimal deliveryCharge;
    private String deliveryPaidBy; // Customer, Business, etc.
    private String deliveryNotes;

    // ═══════════════════════════════════════════════════════════════
    // NOTES & METADATA
    // ═══════════════════════════════════════════════════════════════

    private String saleNotes;
}
