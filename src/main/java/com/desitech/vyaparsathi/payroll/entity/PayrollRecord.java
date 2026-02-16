package com.desitech.vyaparsathi.payroll.entity;

import com.desitech.vyaparsathi.common.entities.ShopAwareEntity;
import com.desitech.vyaparsathi.payroll.enums.PayrollStatus;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "payroll_record")
@Getter @Setter @NoArgsConstructor
public class PayrollRecord extends ShopAwareEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "staff_id", nullable = false)
    private Staff staff;

    @Column(nullable = false)
    private String salaryMonth;

    @Column(nullable = false)
    private Integer salaryYear;

    private LocalDate paymentDate;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal baseSalaryAtTime;

    @Column(precision = 10, scale = 2)
    private BigDecimal bonus = BigDecimal.ZERO;

    @Column(precision = 10, scale = 2)
    private BigDecimal deductions = BigDecimal.ZERO;

    @Column(precision = 10, scale = 2)
    private BigDecimal advanceDeduction = BigDecimal.ZERO;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal netAmount;

    @Enumerated(EnumType.STRING)
    private PayrollStatus status;

    private String paymentMode;

    private String remarks;
}