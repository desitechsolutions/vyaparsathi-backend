package com.desitech.vyaparsathi.receipt.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Payload extracted from a signed receipt-access JWT.
 * Mirrors {@link com.desitech.vyaparsathi.invoice.dto.InvoiceTokenData}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReceiptTokenData {
    private Long receiptId;
    private String receiptNumber;
}
