package com.desitech.vyaparsathi.payroll.entity;

import com.desitech.vyaparsathi.common.entities.ShopAwareEntity;
import com.desitech.vyaparsathi.payroll.enums.LoanStatus;
import com.desitech.vyaparsathi.payroll.enums.LoanType;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Entity
@Table(name = "staff_loans", indexes = {
        @Index(name = "idx_employee", columnList = "employee_id"),
        @Index(name = "idx_status", columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class StaffLoan extends ShopAwareEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "ENUM('SALARY_ADVANCE','EMERGENCY_LOAN','EQUIPMENT_LOAN','PERSONAL_LOAN') DEFAULT 'SALARY_ADVANCE'")
    private LoanType loanType = LoanType.SALARY_ADVANCE;

    @Column(nullable = false, length = 50)
    private String loanNumber;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal principalAmount;

    @Column(precision = 5, scale = 2)
    private BigDecimal interestRateAnnual = BigDecimal.ZERO;

    @Column(nullable = false)
    private Integer tenureMonths = 1;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal monthlyEMI;

    @Column(precision = 12, scale = 2)
    private BigDecimal totalRepaid = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal remainingBalance;

    @Column(nullable = false)
    private LocalDate disbursementDate;

    @Column(nullable = false, length = 20)
    private String recoveryStartMonth;

    @Column(length = 20)
    private String recoveryEndMonth;

    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "ENUM('ACTIVE','CLOSED','DEFAULTED','WRITTEN_OFF') DEFAULT 'ACTIVE'")
    private LoanStatus status = LoanStatus.ACTIVE;

    @Column(columnDefinition = "TEXT")
    private String remarks;

    @OneToMany(mappedBy = "loan", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<StaffLoanRepayment> repayments;
}
