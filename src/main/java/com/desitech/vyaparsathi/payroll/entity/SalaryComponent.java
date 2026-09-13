package com.desitech.vyaparsathi.payroll.entity;

import com.desitech.vyaparsathi.payroll.enums.ComponentType;
import com.desitech.vyaparsathi.payroll.enums.CalculationType;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "salary_components", indexes = {
        @Index(name = "idx_structure", columnList = "structure_id"),
        @Index(name = "idx_type", columnList = "component_type")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SalaryComponent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "shop_id", nullable = false)
    private Long shopId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "structure_id", nullable = false)
    private SalaryStructure structure;

    @Column(nullable = false, length = 100)
    private String componentName;

    @Column(nullable = false, length = 50)
    private String componentCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ComponentType componentType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CalculationType calculationType;

    @Column(nullable = false, precision = 10, scale = 4)
    private BigDecimal calculationValue = BigDecimal.ZERO;

    @Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT TRUE")
    private Boolean isTaxable = true;

    @Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT TRUE")
    private Boolean affectsPF = true;

    @Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT TRUE")
    private Boolean affectsESI = true;

    @Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
    private Boolean isStatutory = false;

    @Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT TRUE")
    private Boolean isActive = true;

    @Column(nullable = false)
    private Integer orderSequence = 0;

    // Convenience getters for common field names
    public String getName() {
        return this.componentName;
    }

    public BigDecimal getAmount() {
        return this.calculationValue;
    }
}
