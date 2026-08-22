package com.desitech.vyaparsathi.payroll.exception;

public class BankingIntegrationException extends RuntimeException {
    public BankingIntegrationException(String message) {
        super(message);
    }

    public BankingIntegrationException(String message, Throwable cause) {
        super(message, cause);
    }
}
