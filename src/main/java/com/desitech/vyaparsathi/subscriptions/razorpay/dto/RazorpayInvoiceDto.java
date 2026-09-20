package com.desitech.vyaparsathi.subscriptions.razorpay.dto;

import com.desitech.vyaparsathi.platform.dto.PlatformDetailsDto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Enterprise B2B subscription invoice DTO combining payment log, platform vendor details,
 * and buyer shop snapshot for full GST Rule 46 compliance.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RazorpayInvoiceDto {

    private Long id;
    private Long shopId;
    private String razorpaySubscriptionId;
    private String razorpayPaymentId;
    private String razorpayInvoiceId;
    private String razorpayOrderId;
    private BigDecimal amount;
    private String currency;
    private String status;
    private String invoiceStatus;
    private String method;
    private String cardId;
    private String bank;
    private String vpa;
    private String errorCode;
    private String errorDescription;
    private LocalDateTime createdAt;

    // Computed invoice fields
    private String invoiceNumber;
    private String planCode;
    private String billingCycle;
    private LocalDateTime periodStart;
    private LocalDateTime periodEnd;

    // Platform (Seller) Snapshot from platform_details table
    private PlatformDetailsDto platformDetails;

    // Buyer (Shop) Snapshot
    private ShopInvoiceSnapshotDto shopDetails;
}
