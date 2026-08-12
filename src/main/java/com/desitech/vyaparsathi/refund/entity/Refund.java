package com.desitech.vyaparsathi.refund.entity;

import com.desitech.vyaparsathi.common.entities.ShopAwareEntity;
import com.desitech.vyaparsathi.payment.enums.PaymentMethod;
import com.desitech.vyaparsathi.refund.enums.RefundStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Money paid back to a customer against a specific {@code Payment}.
 * FKs are raw {@link Long}s to match the {@code Payment} entity's convention.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "refund",
        uniqueConstraints = @UniqueConstraint(name = "uk_refund_shop_number",
                columnNames = {"shop_id", "refund_no"}))
public class Refund extends ShopAwareEntity {

    @Column(name = "refund_no", nullable = false, length = 80)
    private String refundNo;

    @Column(name = "original_payment_id", nullable = false)
    private Long originalPaymentId;

    @Column(name = "customer_id")
    private Long customerId;

    @Column(name = "refund_date", nullable = false)
    private LocalDateTime refundDate;

    @Column(name = "amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 30)
    private PaymentMethod paymentMethod;

    @Column(name = "reference", length = 255)
    private String reference;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private RefundStatus status = RefundStatus.COMPLETED;
}
