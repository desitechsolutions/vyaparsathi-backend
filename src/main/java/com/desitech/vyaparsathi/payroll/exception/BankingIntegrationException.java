package com.desitech.vyaparsathi.payroll.exception;

public class BankingIntegrationException extends RuntimeException {

    private final boolean retryable;

    public BankingIntegrationException(String message) {
        super(message);
        this.retryable = false;
    }

    public BankingIntegrationException(String message, Throwable cause) {
        super(message, cause);
        this.retryable = false;
    }

    public BankingIntegrationException(String message, boolean retryable) {
        super(message);
        this.retryable = retryable;
    }

    public BankingIntegrationException(String message, Throwable cause, boolean retryable) {
        super(message, cause);
        this.retryable = retryable;
    }

    public boolean isRetryable() {
        return retryable;
    }
}
