package com.desitech.vyaparsathi.document.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One row on the items table of any enterprise document. Kept
 * intentionally flat so the renderer stays column-agnostic.
 */
@Getter
@Setter
@NoArgsConstructor
public class LineItemDto {

    private Integer lineNo;
    private String itemCode;
    private String description;
    private String hsnSac;
    private String uom;

    private BigDecimal quantity;
    private BigDecimal unitPrice;

    // Discount can be percentage or flat amount, applied before tax.
    private BigDecimal discountAmount;
    private BigDecimal discountPct;

    private BigDecimal taxableValue;
    private BigDecimal taxRatePct;

    private BigDecimal cgstRatePct;
    private BigDecimal cgstAmount;
    private BigDecimal sgstRatePct;
    private BigDecimal sgstAmount;
    private BigDecimal igstRatePct;
    private BigDecimal igstAmount;
    private BigDecimal cessAmount;

    private BigDecimal lineTotal;

    // Optional: batch/expiry/serial (rendered as sub-line when present)
    private String batchNumber;
    private LocalDate manufactureDate;
    private LocalDate expiryDate;
    private String serialNumbers;

    // Optional: reason (used on RTV / damage docs)
    private String reason;

    // Optional: ordered/received/damaged breakdown for GRN
    private BigDecimal orderedQty;
    private BigDecimal receivedQty;
    private BigDecimal damagedQty;
    private BigDecimal rejectedQty;
    private BigDecimal acceptedQty;
}
