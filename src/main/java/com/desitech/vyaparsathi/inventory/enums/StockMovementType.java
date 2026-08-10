package com.desitech.vyaparsathi.inventory.enums;

public enum StockMovementType {
    ADD, DEDUCT, ADJUST, PURCHASE_RETURN,
    /** Stock transferred out to another location/shop. */
    TRANSFER_OUT,
    /** Stock received from another location/shop. */
    TRANSFER_IN
}
