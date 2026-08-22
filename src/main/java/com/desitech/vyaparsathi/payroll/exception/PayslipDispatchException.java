package com.desitech.vyaparsathi.payroll.exception;

public class PayslipDispatchException extends RuntimeException {
    public PayslipDispatchException(String message) {
        super(message);
    }

    public PayslipDispatchException(String message, Throwable cause) {
        super(message, cause);
    }
}
