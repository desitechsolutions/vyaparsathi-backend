package com.desitech.vyaparsathi.receiving.entity;

import com.desitech.vyaparsathi.common.entities.ShopAwareEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Supplier-provided Advance Shipment Notice. Populated via EDI/CSV import or,
 * later, by the supplier portal. When a matching GRN is created the ASN
 * pre-fills the header (dispatch date, expected arrival, carrier, vehicle,
 * cartons/weight); the {@link #consumedReceivingId} field pins the link so a
 * subsequent ASN for the same PO knows this shipment is already accounted for.
 */
@Entity
@Table(name = "advance_shipment_notice")
@Getter
@Setter
@NoArgsConstructor
public class AdvanceShipmentNotice extends ShopAwareEntity {

    @Column(name = "purchase_order_id")
    private Long purchaseOrderId;

    @Column(name = "supplier_id")
    private Long supplierId;

    @Column(name = "asn_number", nullable = false, length = 100)
    private String asnNumber;

    @Column(name = "dispatch_date")
    private LocalDate dispatchDate;

    @Column(name = "expected_arrival")
    private LocalDate expectedArrival;

    @Column(name = "carrier", length = 200)
    private String carrier;

    @Column(name = "tracking_number", length = 100)
    private String trackingNumber;

    @Column(name = "vehicle_no", length = 50)
    private String vehicleNo;

    @Column(name = "total_cartons")
    private Integer totalCartons;

    @Column(name = "total_weight_kg", precision = 12, scale = 2)
    private BigDecimal totalWeightKg;

    /** GRN id that consumed this ASN's payload (set when a GRN is created against it). */
    @Column(name = "consumed_receiving_id")
    private Long consumedReceivingId;

    @Column(name = "status", nullable = false, length = 30)
    private String status = "PENDING";

    /** Optional JSON blob for line-level ASN data (batch/serial hints). */
    @Column(name = "payload_json", columnDefinition = "TEXT")
    private String payloadJson;

    @Column(name = "notes", length = 500)
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
