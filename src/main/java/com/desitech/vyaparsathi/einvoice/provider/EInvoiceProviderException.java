package com.desitech.vyaparsathi.einvoice.provider;

/**
 * Wraps every failure from an {@link EInvoiceProvider} — network, timeout,
 * IRP validation error, or authentication failure. The {@code errorCode}
 * field carries the IRP's own numeric code when available (e.g. "2150 —
 * Duplicate IRN") so the caller can decide whether to retry, fix data,
 * or surface to the shop owner.
 */
public class EInvoiceProviderException extends RuntimeException {

    private final String errorCode;

    public EInvoiceProviderException(String message) {
        this(message, null, null);
    }

    public EInvoiceProviderException(String message, String errorCode) {
        this(message, errorCode, null);
    }

    public EInvoiceProviderException(String message, String errorCode, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() { return errorCode; }
}
