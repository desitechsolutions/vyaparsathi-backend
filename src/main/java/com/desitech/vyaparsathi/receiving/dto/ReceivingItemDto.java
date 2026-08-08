package com.desitech.vyaparsathi.receiving.dto;

import com.desitech.vyaparsathi.receiving.enums.ReceivingItemStatus;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Min;
import lombok.Data;

import java.time.LocalDate;

@Data
public class ReceivingItemDto {
    private Long id;
    private Long purchaseOrderItemId;
    private Long itemVariantId;
    private String sku;
    private String name;
    private java.math.BigDecimal unitCost;
    private ReceivingItemStatus status;
    @Min(value = 0, message = "Expected quantity cannot be negative")
    private Integer expectedQty;
    @Min(value = 0, message = "Received quantity cannot be negative")
    private Integer receivedQty;
    @Min(value = 0, message = "Damaged quantity cannot be negative")
    private Integer damagedQty;
    private String damageReason;
    private String notes;
    private String putAwayStatus;
    @Min(value = 0, message = "Rejected quantity cannot be negative")
    private Integer rejectedQty;
    private String rejectReason;
    @Min(value = 0, message = "Putaway quantity cannot be negative")
    private Integer putawayQty;
    private String overageReason;
    private String overageNotes;
    private Boolean isOveraged = false;

    public Integer getAcceptedQty() {
        int r = receivedQty != null ? receivedQty : 0;
        int d = damagedQty != null ? damagedQty : 0;
        int rej = rejectedQty != null ? rejectedQty : 0;
        return Math.max(0, r - d - rej);
    }

    public Integer getShortQty() {
        int exp = expectedQty != null ? expectedQty : 0;
        int r = receivedQty != null ? receivedQty : 0;
        return Math.max(0, exp - r);
    }

    public Integer getExcessQty() {
        int exp = expectedQty != null ? expectedQty : 0;
        int r = receivedQty != null ? receivedQty : 0;
        return Math.max(0, r - exp);
    }

    public java.math.BigDecimal getLineTotal() {
        if (unitCost == null) return java.math.BigDecimal.ZERO;
        return unitCost.multiply(java.math.BigDecimal.valueOf(getAcceptedQty()));
    }

    // --- Pharmacy-specific fields ---
    /** Batch/lot number printed on the medicine packaging received from supplier. */
    private String batchNumber;
    /** Manufacturing date printed on the packaging. */
    private LocalDate manufacturingDate;
    /** Expiry date printed on the packaging. Required QC check for pharmacy receiving. */
    private LocalDate expiryDate;

    // --- Electronics-specific fields ---
    /** Serial / IMEI numbers captured at goods receipt (comma-separated for multiple units). */
    private String serialNumber;
    /** Warranty start date recorded when the electronics unit is received. */
    private LocalDate warrantyStartDate;

    // --- Automobile-specific fields ---
    /** OEM part reference number (e.g. manufacturer part number). */
    private String partReference;
}
