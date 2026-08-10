package com.desitech.vyaparsathi.subscriptions.razorpay.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Stores the Razorpay Customer ID created once per shop.
 * Used as the {@code customer_id} field when creating Razorpay subscriptions
 * so that the same customer record is reused for every plan change.
 */
@Entity
@Table(name = "razorpay_customer")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RazorpayCustomer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** VyaparSathi shop identifier — 1:1 with a Razorpay customer record. */
    @Column(name = "shop_id", nullable = false, unique = true)
    private Long shopId;

    /** Razorpay customer ID (e.g. {@code cust_XXXX}). */
    @Column(name = "razorpay_customer_id", nullable = false, unique = true, length = 100)
    private String razorpayCustomerId;

    @Column(length = 150)
    private String email;

    @Column(length = 20)
    private String contact;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
