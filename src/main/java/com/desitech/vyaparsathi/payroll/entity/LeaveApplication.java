package com.desitech.vyaparsathi.payroll.entity;

import com.desitech.vyaparsathi.common.entities.ShopAwareEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "leave_applications")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LeaveApplication extends ShopAwareEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "leave_type_id", nullable = false)
    private LeaveType leaveType;

    @Column(nullable = false)
    private LocalDate fromDate;

    @Column(nullable = false)
    private LocalDate toDate;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal days;

    @Column(columnDefinition = "TEXT")
    private String reason;

    @Column(nullable = false, length = 50)
    private String status; // DRAFT, PENDING, APPROVED, REJECTED, CANCELLED

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approver_id")
    private Employee approver;

    @Column(columnDefinition = "TEXT")
    private String approvalComments;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;
}
