package com.desitech.vyaparsathi.payroll.exception;

public class PayrollCalculationException extends RuntimeException {
    public PayrollCalculationException(String message) {
        super(message);
    }

    public PayrollCalculationException(String message, Throwable cause) {
        super(message, cause);
    }
}
