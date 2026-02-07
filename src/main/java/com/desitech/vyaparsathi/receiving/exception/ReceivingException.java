package com.desitech.vyaparsathi.receiving.exception;

import com.desitech.vyaparsathi.common.exception.ApplicationException;

public class ReceivingException extends ApplicationException {
    public ReceivingException(String message) {
        super(message);
    }
    public ReceivingException(String message, Throwable cause) {
        super(message, cause);
    }
}
