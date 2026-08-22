package com.desitech.vyaparsathi.payroll.entity;

import com.desitech.vyaparsathi.common.entities.ShopAwareEntity;
import com.desitech.vyaparsathi.payroll.enums.AttendanceType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Table(name = "attendance_records", indexes = {
        @Index(name = "idx_employee_month", columnList = "employee_id,attendance_date"),
        @Index(name = "idx_shop_date", columnList = "shop_id,attendance_date")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AttendanceRecord extends ShopAwareEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Column(nullable = false)
    private LocalDate attendanceDate;

    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "ENUM('PRESENT','ABSENT','HALF_DAY','PAID_LEAVE','UNPAID_LEAVE','HOLIDAY','WEEKEND') DEFAULT 'PRESENT'")
    private AttendanceType attendanceType = AttendanceType.PRESENT;
}
