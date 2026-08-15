package com.desitech.vyaparsathi.receiving.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Payload carried in the signed URL that gates {@code GET /api/receiving/signed}.
 * Mirrors {@code PurchaseOrderTokenData} / {@code QuotationTokenData} so the
 * shared JWT machinery has a consistent "id + human-readable number" shape.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReceivingTokenData {
    private Long receivingId;
    private String grNumber;
}