package com.desitech.vyaparsathi.einvoice.provider;

/**
 * Provider-layer failure — connectivity issue, auth failure, or GSP-side
 * validation rejection. The service catches this and translates to an
 * application-level exception the controller can surface to the user.
 */
public class EWayBillProviderException extends RuntimeException {
    private final String providerName;

    public EWayBillProviderException(String providerName, String message) {
        super(message);
        this.providerName = providerName;
    }

    public EWayBillProviderException(String providerName, String message, Throwable cause) {
        super(message, cause);
        this.providerName = providerName;
    }

    public String getProviderName() { return providerName; }
}
