package com.desitech.vyaparsathi.payroll.entity;

import com.desitech.vyaparsathi.common.entities.ShopAwareEntity;
import com.desitech.vyaparsathi.payroll.enums.PayoutStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Entity
@Table(name = "payroll_slips", indexes = {
        @Index(name = "idx_employee", columnList = "employee_id"),
        @Index(name = "idx_payout_status", columnList = "payout_status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PayrollSlip extends ShopAwareEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payroll_run_id", nullable = false)
    private PayrollRun payrollRun;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Column(nullable = false, length = 50)
    private String slipNumber;

    // Attendance Breakdown
    @Column(nullable = false)
    private Integer totalDays;

    @Column(nullable = false)
    private Integer workingDays;

    @Column(nullable = false, precision = 4, scale = 1)
    private BigDecimal presentDays;

    @Column(precision = 4, scale = 1)
    private BigDecimal paidLeaves = BigDecimal.ZERO;

    @Column(precision = 4, scale = 1)
    private BigDecimal lossOfPayDays = BigDecimal.ZERO;

    @Column(precision = 5, scale = 2)
    private BigDecimal overtimeHours = BigDecimal.ZERO;

    // Financial Breakdown
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal monthlyBaseSalary;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal grossEarnings;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal totalDeductions;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal netSalary;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal employerContributions = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal totalCTC;

    // Statutory Split
    @Column(precision = 10, scale = 2)
    private BigDecimal epfEmployee = BigDecimal.ZERO;

    @Column(precision = 10, scale = 2)
    private BigDecimal epfEmployer = BigDecimal.ZERO;

    @Column(precision = 10, scale = 2)
    private BigDecimal esiEmployee = BigDecimal.ZERO;

    @Column(precision = 10, scale = 2)
    private BigDecimal esiEmployer = BigDecimal.ZERO;

    @Column(precision = 10, scale = 2)
    private BigDecimal professionalTax = BigDecimal.ZERO;

    @Column(precision = 10, scale = 2)
    private BigDecimal tdsTax = BigDecimal.ZERO;

    @Column(precision = 10, scale = 2)
    private BigDecimal loanAdvanceDeduction = BigDecimal.ZERO;

    // Payout Tracking
    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "ENUM('UNPAID','QUEUED','PAID','FAILED') DEFAULT 'UNPAID'")
    private PayoutStatus payoutStatus = PayoutStatus.UNPAID;

    @Column(length = 30)
    private String paymentMode;

    @Column(length = 100)
    private String bankUTRReference;

    private LocalDate disbursedOn;

    @Column(length = 255)
    private String pdfDocumentUrl;

    @OneToMany(mappedBy = "payrollSlip", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<PayrollSlipItem> items;
}
