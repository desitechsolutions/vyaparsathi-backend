package com.desitech.vyaparsathi.payroll.entity;

import com.desitech.vyaparsathi.common.entities.ShopAwareEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "leave_types", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"shop_id", "name"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LeaveType extends ShopAwareEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false)
    private Integer maxDaysPerYear;

    @Column(nullable = false)
    private Integer carryForwardDays;

    @Column(nullable = false)
    private Boolean isProrated;

    @Column(nullable = false)
    private Boolean isActive;
}
