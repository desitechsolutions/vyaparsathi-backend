package com.desitech.vyaparsathi.delivery.service;

import com.desitech.vyaparsathi.delivery.enums.DeliveryStatus;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import static com.desitech.vyaparsathi.delivery.enums.DeliveryStatus.*;

/**
 * State machine for {@link DeliveryStatus}. Rejects backward or nonsense
 * transitions such as {@code DELIVERED → PENDING}.
 *
 * Rules:
 *   PENDING           → PACKED, OUT_FOR_DELIVERY, CANCELLED
 *   PACKED            → OUT_FOR_DELIVERY, IN_TRANSIT, CANCELLED
 *   OUT_FOR_DELIVERY  → IN_TRANSIT, DELIVERED, CANCELLED
 *   IN_TRANSIT        → OUT_FOR_DELIVERY (re-attempt), DELIVERED, CANCELLED
 *   DELIVERED         → (terminal)
 *   CANCELLED         → (terminal)
 *
 * A no-op transition (target equals current) is treated as allowed so
 * repeated PATCHes are idempotent — see {@link #isNoOp}.
 */
public final class DeliveryTransitions {

    private static final Map<DeliveryStatus, Set<DeliveryStatus>> ALLOWED = Map.of(
            PENDING,          EnumSet.of(PACKED, OUT_FOR_DELIVERY, CANCELLED),
            PACKED,           EnumSet.of(OUT_FOR_DELIVERY, IN_TRANSIT, CANCELLED),
            OUT_FOR_DELIVERY, EnumSet.of(IN_TRANSIT, DELIVERED, CANCELLED),
            IN_TRANSIT,       EnumSet.of(OUT_FOR_DELIVERY, DELIVERED, CANCELLED),
            DELIVERED,        EnumSet.noneOf(DeliveryStatus.class),
            CANCELLED,        EnumSet.noneOf(DeliveryStatus.class)
    );

    private DeliveryTransitions() {}

    public static boolean isAllowed(DeliveryStatus from, DeliveryStatus to) {
        if (from == null || to == null) return false;
        if (from == to) return true;  // idempotent PATCH
        return ALLOWED.getOrDefault(from, EnumSet.noneOf(DeliveryStatus.class)).contains(to);
    }

    public static boolean isNoOp(DeliveryStatus from, DeliveryStatus to) {
        return from != null && from == to;
    }

    public static boolean isTerminal(DeliveryStatus s) {
        return s == DELIVERED || s == CANCELLED;
    }

    public static Set<DeliveryStatus> nextAllowed(DeliveryStatus from) {
        return ALLOWED.getOrDefault(from, EnumSet.noneOf(DeliveryStatus.class));
    }
}
