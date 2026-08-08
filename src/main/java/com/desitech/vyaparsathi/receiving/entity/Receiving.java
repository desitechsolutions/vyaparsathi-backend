package com.desitech.vyaparsathi.receiving.entity;

import com.desitech.vyaparsathi.common.entities.BaseEntity;
import com.desitech.vyaparsathi.common.entities.ShopAwareEntity;
import com.desitech.vyaparsathi.purchaseorder.entity.PurchaseOrder;
import com.desitech.vyaparsathi.receiving.enums.ReceivingStatus;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@Entity
@EntityListeners(AuditingEntityListener.class)
public class Receiving extends ShopAwareEntity {

    @Column(name = "gr_number", length = 50)
    private String grNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "purchase_order_id", nullable = false)
    private PurchaseOrder purchaseOrder;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReceivingStatus status;

    @Column(name = "supplier_invoice_no", length = 100)
    private String supplierInvoiceNo;

    @Column(name = "supplier_invoice_date")
    private java.time.LocalDate supplierInvoiceDate;

    @Column(name = "vehicle_no", length = 50)
    private String vehicleNo;

    @Column(name = "delivery_challan_no", length = 100)
    private String deliveryChallanNo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by_user_id")
    private com.desitech.vyaparsathi.auth.entity.User approvedByUser;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @CreatedDate
    private LocalDateTime receivedAt;

    @CreatedBy
    @Column(nullable = false, updatable = false)
    private String receivedBy;

    private String notes;

    @OneToMany(mappedBy = "receiving", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ReceivingItem> items;

    @LastModifiedDate
    private LocalDateTime lastUpdatedAt;  // Added for auditing
}