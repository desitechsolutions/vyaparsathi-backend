package com.desitech.vyaparsathi.sales.entity;

import com.desitech.vyaparsathi.common.entities.BaseEntity;
import com.desitech.vyaparsathi.common.entities.ShopAwareEntity;
import com.desitech.vyaparsathi.common.util.LocalDateTimeAttributeConverter;
import com.desitech.vyaparsathi.customer.entity.Customer;
import com.desitech.vyaparsathi.delivery.entity.Delivery;
import com.desitech.vyaparsathi.delivery.enums.DeliveryStatus;
import com.desitech.vyaparsathi.payment.enums.PaymentStatus;
import com.desitech.vyaparsathi.sales.enums.SaleStatus;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "sale")
public class Sale extends ShopAwareEntity {
    @Column(name = "invoice_no", nullable = false, unique = true, length = 50)
    private String invoiceNo;

    @Convert(converter = LocalDateTimeAttributeConverter.class)
    @Column(nullable = false)
    private LocalDateTime date;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @OneToMany(mappedBy = "sale", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt DESC")
    private List<Delivery> deliveries = new ArrayList<>();

    // optional helper
    public Delivery getLatestDelivery() {
        return deliveries.isEmpty() ? null : deliveries.get(0);
    }

    public boolean hasDelivery() {
        return deliveries != null && !deliveries.isEmpty();
    }

    /**
     * Optional: Get current status without fetching full list
     * (useful when you only need status, not the whole object)
     */
    public DeliveryStatus getCurrentDeliveryStatus() {
        Delivery latest = getLatestDelivery();
        return latest != null ? latest.getDeliveryStatus() : null;
    }

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "round_off", precision = 10, scale = 2)
    private BigDecimal roundOff;

    @Column(name = "synced_flag", nullable = false)
    private boolean syncedFlag = false;

    @OneToMany(mappedBy = "sale", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonManagedReference
    private List<SaleItem> saleItems = new ArrayList<>();

    @Column(name = "payment_status", nullable = false)
    @Enumerated(EnumType.STRING)
    private PaymentStatus paymentStatus = PaymentStatus.PENDING;

    @Column(name = "status", nullable = false)
    @Enumerated(EnumType.STRING)
    private SaleStatus status = SaleStatus.COMPLETED;

    // --- Pharmacy-specific fields ---

    /**
     * Name of the prescribing doctor. Required for Schedule H1 and X (narcotic) drugs.
     */
    @Column(name = "doctor_name", length = 200)
    private String doctorName;

    /**
     * Name of the patient (if different from the customer). Used in narcotics register.
     */
    @Column(name = "patient_name", length = 200)
    private String patientName;

    /**
     * Prescription / Rx number provided by the customer. Used for pharmacy compliance tracking.
     */
    @Column(name = "prescription_number", length = 100)
    private String prescriptionNumber;

    @PrePersist
    @Override
    public void onCreate() {
        super.onCreate();
        if (this.date == null) {
            this.date = LocalDateTime.now();
        }
    }
}