package com.desitech.vyaparsathi.payroll.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * Individual debit/credit line within a payroll GL journal entry.
 * Every journal entry must have balanced total debits = total credits.
 */
@Entity
@Table(name = "payroll_journal_entry_lines", indexes = {
        @Index(name = "idx_jel_entry", columnList = "journal_entry_id"),
        @Index(name = "idx_jel_account", columnList = "account_code")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class JournalEntryLineEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "journal_entry_id", nullable = false)
    private JournalEntryEntity journalEntry;

    @Column(nullable = false, length = 20)
    private String accountCode;

    @Column(nullable = false, length = 100)
    private String accountName;

    @Column(nullable = false, precision = 16, scale = 2)
    private BigDecimal debit = BigDecimal.ZERO;

    @Column(nullable = false, precision = 16, scale = 2)
    private BigDecimal credit = BigDecimal.ZERO;

    @Column(length = 100)
    private String costCenter;

    @Column(length = 255)
    private String narration;
}
