package com.desitech.vyaparsathi.payroll.entity;

import lombok.*;
import jakarta.persistence.*;
import com.desitech.vyaparsathi.common.entities.ShopAwareEntity;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "bank_transactions")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BankTransaction extends ShopAwareEntity {
    @ManyToOne
    @JoinColumn(name = "payroll_run_id")
    private PayrollRun payrollRun;

    @ManyToOne
    @JoinColumn(name = "employee_id")
    private Employee employee;

    @Column(nullable = false, length = 50)
    private String transactionType;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(nullable = false, length = 50)
    private String paymentMethod;

    @Column(length = 100)
    private String referenceNumber;

    @Column(length = 50)
    private String utrNumber;

    @Column(length = 50)
    private String status;

    @Column(length = 20)
    private String bankResponseCode;

    @Column(columnDefinition = "TEXT")
    private String bankResponseMessage;

    private LocalDate initiatedAt;
    private LocalDate completedAt;
}
