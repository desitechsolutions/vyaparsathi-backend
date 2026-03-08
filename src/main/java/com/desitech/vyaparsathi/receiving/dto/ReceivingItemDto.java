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

    // --- Pharmacy-specific fields ---
    /** Batch/lot number printed on the medicine packaging received from supplier. */
    private String batchNumber;
    /** Manufacturing date printed on the packaging. */
    private LocalDate manufacturingDate;
    /** Expiry date printed on the packaging. Required QC check for pharmacy receiving. */
    private LocalDate expiryDate;
}
