package com.desitech.vyaparsathi.payment.exception;

import com.desitech.vyaparsathi.common.exception.ApplicationException;

public class PaymentException extends ApplicationException {
    public PaymentException(String message) {
        super(message);
    }
    public PaymentException(String message, Throwable cause) {
        super(message, cause);
    }
}
