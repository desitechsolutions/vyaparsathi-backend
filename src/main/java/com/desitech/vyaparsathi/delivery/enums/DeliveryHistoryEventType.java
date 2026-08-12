package com.desitech.vyaparsathi.delivery.enums;

/**
 * Distinguishes rows in {@code delivery_status_history}. Status transitions
 * write {@link #STATUS_CHANGE}; reassigning a delivery person writes
 * {@link #ASSIGNMENT}; recording a failed attempt writes {@link #ATTEMPT}.
 */
public enum DeliveryHistoryEventType {
    STATUS_CHANGE,
    ASSIGNMENT,
    ATTEMPT
}
