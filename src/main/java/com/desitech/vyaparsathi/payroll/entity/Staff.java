package com.desitech.vyaparsathi.payroll.entity;

import com.desitech.vyaparsathi.common.entities.ShopAwareEntity;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "staff")
@Getter @Setter @NoArgsConstructor
public class Staff extends ShopAwareEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String phone;

    @Column(nullable = false)
    private String role;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal baseSalary;

    @Column(precision = 10, scale = 2)
    private BigDecimal advanceBalance = BigDecimal.ZERO;

    private LocalDate joiningDate;

    @Column(nullable = false)
    private boolean active = true;
}