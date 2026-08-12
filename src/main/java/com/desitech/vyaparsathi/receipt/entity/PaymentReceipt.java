package com.desitech.vyaparsathi.receipt.entity;

import com.desitech.vyaparsathi.common.entities.ShopAwareEntity;
import com.desitech.vyaparsathi.payment.enums.PaymentMethod;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * A proof-of-payment document issued at the moment a {@code Payment} is recorded.
 * Persisted so the same {@link #receiptNumber} can be re-printed months later.
 *
 * <p>FKs are stored as raw {@link Long}s (not {@code @ManyToOne}) to match the
 * convention used by {@link com.desitech.vyaparsathi.payment.entity.Payment}
 * itself, which also stores {@code customerId} / {@code sourceId} as raw longs.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "payment_receipt",
        uniqueConstraints = @UniqueConstraint(name = "uk_payment_receipt_shop_number",
                columnNames = {"shop_id", "receipt_number"}))
public class PaymentReceipt extends ShopAwareEntity {

    @Column(name = "receipt_number", nullable = false, length = 80)
    private String receiptNumber;

    @Column(name = "payment_id", nullable = false)
    private Long paymentId;

    /** Nullable — an advance-only payment may not be tied to a specific customer. */
    @Column(name = "customer_id")
    private Long customerId;

    @Column(name = "receipt_date", nullable = false)
    private LocalDateTime receiptDate;

    @Column(name = "amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 30)
    private PaymentMethod paymentMethod;

    @Column(name = "reference", length = 255)
    private String reference;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;
}
