package com.desitech.vyaparsathi.einvoice.provider;

import com.desitech.vyaparsathi.einvoice.dto.EWayBillRequestDto;
import com.desitech.vyaparsathi.sales.entity.Sale;

import java.time.LocalDateTime;

/**
 * Strategy for E-Way Bill number generation. Two implementations ship:
 *
 * <ul>
 *   <li>{@code mock} — {@link MockEWayBillProvider}, generates a synthetic 12-digit
 *       number. Default. Used in dev / staging / while GSP credentials are pending.</li>
 *   <li>{@code nic} — {@link NicGspEWayBillProvider}, integrates with a real
 *       GSP-authorized NIC endpoint. Requires {@code vyaparsathi.eway.nic.*}
 *       properties to be filled in.</li>
 * </ul>
 *
 * Select the active provider via {@code vyaparsathi.eway.provider} in
 * application.properties. See {@link EWayBillProviderConfig}.
 *
 * The business layer ({@code EWayBillService}) owns validation (₹50k threshold,
 * idempotency), Sale persistence, and DTO construction. This interface owns ONLY
 * the "call the source of truth to mint a number" step.
 */
public interface EWayBillProvider {

    /**
     * Generate an E-Way Bill number for the given sale. Implementations may
     * hit an external API (NIC) or produce a synthetic number (mock).
     *
     * @throws EWayBillProviderException on connectivity, auth, or server-side
     *         validation failure. The service converts this into an
     *         appropriate application error.
     */
    Result generate(Sale sale, EWayBillRequestDto request);

    /** Short identifier for logs / metrics (e.g. "mock", "nic"). */
    String getProviderName();

    /**
     * What the provider returns to the service layer. Value object only —
     * the service is responsible for stamping these onto the Sale entity.
     */
    class Result {
        private final String ewayBillNo;
        private final LocalDateTime generatedAt;
        private final LocalDateTime validUntil;

        public Result(String ewayBillNo, LocalDateTime generatedAt, LocalDateTime validUntil) {
            this.ewayBillNo = ewayBillNo;
            this.generatedAt = generatedAt;
            this.validUntil = validUntil;
        }

        public String getEwayBillNo() { return ewayBillNo; }
        public LocalDateTime getGeneratedAt() { return generatedAt; }
        public LocalDateTime getValidUntil() { return validUntil; }
    }
}
