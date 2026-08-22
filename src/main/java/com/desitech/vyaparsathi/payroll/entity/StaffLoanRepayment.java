package com.desitech.vyaparsathi.payroll.entity;

import com.desitech.vyaparsathi.payroll.enums.RepaymentStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "staff_loan_repayments", indexes = {
        @Index(name = "idx_status", columnList = "payment_status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class StaffLoanRepayment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "loan_id", nullable = false)
    private StaffLoan loan;

    @Column(nullable = false)
    private Integer installmentNumber;

    @Column(nullable = false)
    private LocalDate dueDate;

    private LocalDate paidDate;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal principalAmount;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal interestAmount;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal totalEMI;

    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "ENUM('PENDING','PAID','OVERDUE','WAIVED') DEFAULT 'PENDING'")
    private RepaymentStatus paymentStatus = RepaymentStatus.PENDING;

    @Column(name = "payroll_slip_id")
    private Long payrollSlipId;

    @Column(columnDefinition = "TEXT")
    private String remarks;
}
