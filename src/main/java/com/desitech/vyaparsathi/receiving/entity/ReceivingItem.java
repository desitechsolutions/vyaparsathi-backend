package com.desitech.vyaparsathi.receiving.entity;

import com.desitech.vyaparsathi.common.entities.BaseEntity;
import com.desitech.vyaparsathi.common.entities.ShopAwareEntity;
import com.desitech.vyaparsathi.purchaseorder.entity.PurchaseOrderItem;
import com.desitech.vyaparsathi.receiving.enums.ReceivingItemStatus;
import jakarta.persistence.*;
import lombok.*;

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
}