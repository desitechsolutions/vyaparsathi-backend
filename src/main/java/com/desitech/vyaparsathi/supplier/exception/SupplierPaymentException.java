package com.desitech.vyaparsathi.supplier.exception;

public class SupplierPaymentException extends RuntimeException {

    public SupplierPaymentException(String message) {
        super(message);
    }

    public SupplierPaymentException(String message, Throwable cause) {
        super(message, cause);
    }
}
