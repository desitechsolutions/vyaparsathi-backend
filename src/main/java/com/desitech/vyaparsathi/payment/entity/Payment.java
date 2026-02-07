package com.desitech.vyaparsathi.payment.entity;

import com.desitech.vyaparsathi.common.entities.BaseEntity;
import com.desitech.vyaparsathi.common.entities.ShopAwareEntity;
import com.desitech.vyaparsathi.payment.enums.PaymentMethod;
import com.desitech.vyaparsathi.payment.enums.PaymentSourceType;
import com.desitech.vyaparsathi.payment.enums.PaymentStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class Payment extends ShopAwareEntity {

    @Column(unique = true)
    private String transactionId;

    private Long sourceId; // PO, Sale, etc.
    @Enumerated(EnumType.STRING)
    private PaymentSourceType sourceType; // "PURCHASE_ORDER", "SALE", etc.

    private Long supplierId;
    private Long customerId;

    private BigDecimal amount;
    private LocalDateTime paymentDate;
    @Enumerated(EnumType.STRING)
    private PaymentMethod paymentMethod;
    private String reference;
    private String notes;

    @Enumerated(EnumType.STRING)
    private PaymentStatus status;
    @PrePersist
    @Override
    public void onCreate() {
        super.onCreate();
        if (paymentDate == null) {
            paymentDate = LocalDateTime.now();
        }
    }
}