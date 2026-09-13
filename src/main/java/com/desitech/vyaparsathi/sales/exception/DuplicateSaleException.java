package com.desitech.vyaparsathi.sales.exception;

/**
 * Thrown when a sale with the same idempotency key (clientTxnId) already exists.
 * Caught by OfflineSalesProcessorService to classify the record as CONFLICT
 * instead of FAILED, preventing pointless retries.
 */
public class DuplicateSaleException extends RuntimeException {
    private final String clientTxnId;

    public DuplicateSaleException(String clientTxnId) {
        super("Sale already exists for clientTxnId: " + clientTxnId);
        this.clientTxnId = clientTxnId;
    }

    public String getClientTxnId() {
        return clientTxnId;
    }
}
