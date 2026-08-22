package com.desitech.vyaparsathi.payroll.entity;

import lombok.*;
import jakarta.persistence.*;
import com.desitech.vyaparsathi.common.entities.ShopAwareEntity;
import java.time.LocalDate;

@Entity
@Table(name = "payslip_dispatch_logs")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PayslipDispatchLog extends ShopAwareEntity {
    @ManyToOne
    @JoinColumn(name = "payroll_slip_id", nullable = false)
    private PayrollSlip payrollSlip;

    @ManyToOne
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Column(nullable = false, length = 50)
    private String dispatchMethod;

    @Column(nullable = false, length = 255)
    private String recipientAddress;

    private LocalDate sentAt;

    @Column(length = 50)
    private String deliveryStatus;

    @Column(length = 100)
    private String providerReference;

    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    @Column(nullable = false)
    private Integer retryCount = 0;
}
