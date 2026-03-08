package com.desitech.vyaparsathi.receiving.entity;

import com.desitech.vyaparsathi.common.entities.BaseEntity;
import com.desitech.vyaparsathi.common.entities.ShopAwareEntity;
import com.desitech.vyaparsathi.purchaseorder.entity.PurchaseOrderItem;
import com.desitech.vyaparsathi.receiving.enums.ReceivingItemStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Setter
@Getter
@NoArgsConstructor
@Entity
public class ReceivingItem extends ShopAwareEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "receiving_id", nullable = false)
    private Receiving receiving;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "po_item_id", nullable = false)
    private PurchaseOrderItem purchaseOrderItem;

    @Enumerated(EnumType.STRING)
    private ReceivingItemStatus status;

    @Column(nullable = false)
    private Integer expectedQty;

    @Column(nullable = false)
    private Integer receivedQty;

    private Integer damagedQty;

    private String damageReason;

    private Integer rejectedQty;

    private String rejectReason;

    private String notes;

    @Column(name = "putaway_qty")
    private Integer putawayQty;

    @Column(name = "put_away_status")
    private String putAwayStatus;

    @Column(name = "is_overaged")
    private Boolean isOveraged = false;

    @Column(name = "overage_reason")
    private String overageReason; // E.g., VENDOR_MIS-SHIPMENT

    @Column(name = "overage_notes", length = 500)
    private String overageNotes;

    // --- Pharmacy-specific fields ---

    /**
     * Batch/lot number received from the supplier. Mandatory for pharmacy stock traceability.
     */
    @Column(name = "batch_number")
    private String batchNumber;

    /**
     * Manufacturing date printed on the medicine packaging.
     */
    @Column(name = "manufacturing_date")
    private LocalDate manufacturingDate;

    /**
     * Expiry date printed on the medicine packaging. Used for QC validation at receiving.
     */
    @Column(name = "expiry_date")
    private LocalDate expiryDate;
}
