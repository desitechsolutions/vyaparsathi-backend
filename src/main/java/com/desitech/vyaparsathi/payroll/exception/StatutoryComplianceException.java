package com.desitech.vyaparsathi.payroll.exception;

public class StatutoryComplianceException extends RuntimeException {
    public StatutoryComplianceException(String message) {
        super(message);
    }

    public StatutoryComplianceException(String message, Throwable cause) {
        super(message, cause);
    }
}
