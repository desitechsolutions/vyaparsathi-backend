package com.desitech.vyaparsathi.salesorder.enums;

/**
 * Lifecycle of a {@link com.desitech.vyaparsathi.salesorder.entity.SalesOrder}.
 * Mirrored by the CHECK constraint on {@code sales_order.status} in V64.
 *
 * <p>Allowed transitions:
 * <pre>
 *   DRAFT               → APPROVED | CANCELLED
 *   APPROVED            → PARTIALLY_FULFILLED | FULFILLED | CANCELLED
 *   PARTIALLY_FULFILLED → PARTIALLY_FULFILLED | FULFILLED | CANCELLED
 *   FULFILLED           → (terminal)
 *   CANCELLED           → (terminal)
 * </pre>
 */
public enum SalesOrderStatus {
    DRAFT,
    APPROVED,
    PARTIALLY_FULFILLED,
    FULFILLED,
    CANCELLED
}
