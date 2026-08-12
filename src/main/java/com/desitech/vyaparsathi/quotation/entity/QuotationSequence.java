package com.desitech.vyaparsathi.quotation.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "quotation_sequence")
@Getter
@Setter
@NoArgsConstructor
public class QuotationSequence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "shop_id", nullable = false)
    private Long shopId;

    @Column(name = "prefix", nullable = false, length = 100)
    private String prefix = "QT";

    @Column(name = "fiscal_year", nullable = false)
    private short fiscalYear;

    @Column(name = "last_seq", nullable = false)
    private long lastSeq = 0L;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
