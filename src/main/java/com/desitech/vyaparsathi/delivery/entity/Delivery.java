package com.desitech.vyaparsathi.delivery.entity;

import com.desitech.vyaparsathi.common.entities.BaseEntity;
import com.desitech.vyaparsathi.common.entities.ShopAwareEntity;
import com.desitech.vyaparsathi.delivery.enums.DeliveryPaidBy;
import com.desitech.vyaparsathi.delivery.enums.DeliveryStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "deliveries")
public class Delivery extends ShopAwareEntity {

    @Column(nullable = false)
    private Long saleId;

    private String invoiceNumber;
    private String customerName;

    @Column(columnDefinition = "TEXT")
    private String deliveryAddress;

    private Double deliveryCharge;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DeliveryPaidBy deliveryPaidBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DeliveryStatus deliveryStatus = DeliveryStatus.PENDING;

    @ManyToOne
    @JoinColumn(name = "delivery_person_id")
    private DeliveryPerson deliveryPerson;

    private String deliveryNotes;

    private LocalDateTime deliveredAt;

    @OneToMany(mappedBy = "delivery", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<DeliveryStatusHistory> statusHistory;
}