package com.desitech.vyaparsathi.payroll.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Persisted General Ledger journal entry header for payroll postings.
 * Each payroll run generates exactly one balanced journal entry.
 */
@Entity
@Table(name = "payroll_journal_entries", indexes = {
        @Index(name = "idx_jl_payroll_run", columnList = "payroll_run_id"),
        @Index(name = "idx_jl_shop_date", columnList = "shop_id,transaction_date")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class JournalEntryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "shop_id", nullable = false)
    private Long shopId;

    @Column(name = "payroll_run_id", nullable = false)
    private Long payrollRunId;

    @Column(nullable = false)
    private LocalDate transactionDate;

    @Column(nullable = false, length = 100, unique = true)
    private String referenceNumber;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false, precision = 16, scale = 2)
    private BigDecimal totalDebit = BigDecimal.ZERO;

    @Column(nullable = false, precision = 16, scale = 2)
    private BigDecimal totalCredit = BigDecimal.ZERO;

    @Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
    private Boolean isBalanced = false;

    private LocalDateTime postedAt;

    @Column(name = "posted_by_user_id")
    private Long postedByUserId;

    @OneToMany(mappedBy = "journalEntry", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<JournalEntryLineEntity> lines = new ArrayList<>();
}
