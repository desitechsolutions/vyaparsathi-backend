package com.desitech.vyaparsathi.purchaseorder.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Body for {@code POST /api/purchase-orders/{id}/reject} (V85). The reason
 * is required — the requester needs to know what to change before resubmitting,
 * and the audit trail should never carry a blank rejection.
 */
@Data
public class PurchaseOrderRejectDto {

    @NotBlank(message = "A rejection reason is required")
    @Size(max = 500, message = "Reason must be 500 characters or fewer")
    private String reason;
}