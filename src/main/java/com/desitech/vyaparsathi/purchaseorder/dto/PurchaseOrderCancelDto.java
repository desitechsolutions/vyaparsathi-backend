package com.desitech.vyaparsathi.purchaseorder.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Body for {@code POST /api/purchase-orders/{id}/cancel} (V81). The reason
 * is required — cancelling a committed PO is an audit-sensitive event and
 * the shop must record why.
 */
@Data
public class PurchaseOrderCancelDto {

    @NotBlank(message = "A cancellation reason is required")
    @Size(max = 500, message = "Reason must be 500 characters or fewer")
    private String reason;
}
