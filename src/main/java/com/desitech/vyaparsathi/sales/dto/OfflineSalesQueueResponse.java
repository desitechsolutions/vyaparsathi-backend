package com.desitech.vyaparsathi.sales.dto;

import com.desitech.vyaparsathi.sales.enums.OfflineSalesStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Response DTO for offline sales queue operations
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OfflineSalesQueueResponse {

    /**
     * Queue record ID
     */
    private Long id;

    /**
     * Client transaction ID (from frontend)
     */
    private String clientTxnId;

    /**
     * Current status of the queued sale
     */
    private OfflineSalesStatus status;

    /**
     * Temporary offline sale number (shown on receipt)
     * Format: DRAFT-{shopId}-{yyyyMMddHHmmss}
     */
    private String offlineSaleNo;

    /**
     * After processing: actual sale ID
     */
    private Long saleId;

    /**
     * After processing: invoice number
     * Format: INV-{year}-{sequence}
     */
    private String invoiceNumber;

    /**
     * Signed URL for invoice download
     */
    private String invoiceSignedUrl;

    /**
     * Error details (if failed)
     */
    private String errorCode;
    private String errorMessage;

    /**
     * Sale amount — parsed from requestPayloadJson for quick display in the queue drawer.
     * Avoids full JSON deserialization on the client.
     */
    private java.math.BigDecimal totalAmount;

    /**
     * Customer name — parsed from requestPayloadJson for quick display in the queue drawer.
     */
    private String customerName;

    /**
     * How many times this sale has been retried
     */
    private Integer retryCount;

    /**
     * When it was synced/processed
     */
    private LocalDateTime syncedAt;

    /**
     * When record was created
     */
    private LocalDateTime createdAt;

    /**
     * When record was last updated
     */
    private LocalDateTime updatedAt;
}
