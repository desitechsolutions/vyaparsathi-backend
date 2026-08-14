package com.desitech.vyaparsathi.purchaseorder.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Payload carried in the signed URL that gates {@code GET /api/purchase-orders/signed}.
 * Mirrors the QuotationTokenData / InvoiceTokenData pattern so downstream JWT
 * helpers all share the same "id + human-readable number" shape.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PurchaseOrderTokenData {
    private Long purchaseOrderId;
    private String poNumber;
}