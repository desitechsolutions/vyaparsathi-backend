package com.desitech.vyaparsathi.payroll.entity;

import com.desitech.vyaparsathi.common.entities.ShopAwareEntity;
import com.desitech.vyaparsathi.payroll.enums.PayrollRunStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "payroll_runs", indexes = {
        @Index(name = "idx_status", columnList = "status"),
        @Index(name = "idx_shop_period", columnList = "shop_id,payroll_year,payroll_month")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PayrollRun extends ShopAwareEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String runNumber;

    @Column(nullable = false, length = 20)
    private String payrollMonth;

    @Column(nullable = false)
    private Integer payrollYear;

    @Column(nullable = false)
    private LocalDate startDate;

    @Column(nullable = false)
    private LocalDate endDate;

    @Column(nullable = false)
    private Integer calendarDays = 30;

    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "ENUM('DRAFT','PROCESSING','PENDING_APPROVAL','APPROVED','DISBURSED','VOID') DEFAULT 'DRAFT'")
    private PayrollRunStatus status = PayrollRunStatus.DRAFT;

    // Financial Aggregates
    @Column(nullable = false)
    private Integer totalEmployees = 0;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal totalGrossEarnings = BigDecimal.ZERO;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal totalEmployeeDeductions = BigDecimal.ZERO;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal totalNetPayable = BigDecimal.ZERO;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal totalEmployerContributions = BigDecimal.ZERO;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal totalCompanyCost = BigDecimal.ZERO;

    // Approvals & Execution
    @Column(name = "prepared_by_user_id")
    private Long preparedByUserId;

    @Column(name = "approved_by_user_id")
    private Long approvedByUserId;

    private LocalDateTime approvedAt;

    private LocalDateTime disbursedAt;

    @Column(name = "shop_bank_account_id")
    private Long shopBankAccountId;

    @OneToMany(mappedBy = "payrollRun", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<PayrollSlip> payrollSlips;
}
