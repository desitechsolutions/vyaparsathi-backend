package com.desitech.vyaparsathi.common.exception;

public class UserInactiveException extends ApplicationException {

    private final Long retryAfterSeconds;

    public UserInactiveException(String message) {
        super(message);
        this.retryAfterSeconds = null;
    }

    public UserInactiveException(String message, long retryAfterSeconds) {
        super(message);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public Long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
