package com.desitech.vyaparsathi.payroll.entity;

import com.desitech.vyaparsathi.payroll.enums.ComponentType;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "payroll_slip_items", indexes = {
        @Index(name = "idx_slip", columnList = "payroll_slip_id"),
        @Index(name = "idx_component_code", columnList = "component_code")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PayrollSlipItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payroll_slip_id", nullable = false)
    private PayrollSlip payrollSlip;

    @Column(nullable = false, length = 100)
    private String componentName;

    @Column(nullable = false, length = 50)
    private String componentCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ComponentType componentType;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;
}
