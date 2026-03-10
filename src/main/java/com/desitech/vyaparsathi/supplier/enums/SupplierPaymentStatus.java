package com.desitech.vyaparsathi.supplier.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum SupplierPaymentStatus {
    PENDING, PARTIALLY_PAID, PAID;

    @JsonCreator
    public static SupplierPaymentStatus from(String value) {
        return value == null ? null : SupplierPaymentStatus.valueOf(value.toUpperCase());
    }

    @JsonValue
    public String toValue() {
        return this.name();
    }
}
