package com.desitech.vyaparsathi.compliance.exception;

public class LockedPeriodException extends RuntimeException {

    public LockedPeriodException(String period) {
        super("Cannot modify, cancel, or add transactions in a locked tax period: " + period);
    }
}
