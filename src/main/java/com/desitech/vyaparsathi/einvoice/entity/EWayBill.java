package com.desitech.vyaparsathi.einvoice.entity;

import com.desitech.vyaparsathi.common.entities.ShopAwareEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "e_way_bill")
@Getter
@Setter
@NoArgsConstructor
public class EWayBill extends ShopAwareEntity {

    @Column(name = "document_type", nullable = false, length = 30)
    private String documentType;

    @Column(name = "document_id", nullable = false)
    private Long documentId;

    @Column(name = "document_number", length = 50)
    private String documentNumber;

    @Column(name = "ewb_number", nullable = false, unique = true, length = 30)
    private String ewbNumber;

    @Column(name = "generated_at")
    private LocalDateTime generatedAt;

    @Column(name = "valid_till")
    private LocalDateTime validTill;

    @Column(name = "distance_km")
    private Integer distanceKm;

    @Column(name = "transporter_gstin", length = 20)
    private String transporterGstin;

    @Column(name = "transporter_name", length = 120)
    private String transporterName;

    @Column(name = "vehicle_number", length = 20)
    private String vehicleNumber;

    @Column(name = "transport_mode", length = 30)
    private String transportMode;

    /** ACTIVE | CANCELLED | EXPIRED */
    @Column(name = "status", nullable = false, length = 20)
    private String status = "ACTIVE";

    @Column(name = "raw_response", columnDefinition = "MEDIUMTEXT")
    private String rawResponse;
}
