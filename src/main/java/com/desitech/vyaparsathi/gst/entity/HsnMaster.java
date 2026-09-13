package com.desitech.vyaparsathi.gst.entity;

import com.desitech.vyaparsathi.common.entities.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "gst_hsn_master",
    uniqueConstraints = @UniqueConstraint(name = "uq_hsn_code", columnNames = {"hsn_code"}))
@Getter
@Setter
@NoArgsConstructor
public class HsnMaster extends BaseEntity {

    @Column(name = "hsn_code", nullable = false, length = 10, unique = true)
    private String hsnCode;

    @Column(name = "description", nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(name = "gst_type", nullable = false, length = 10)
    private String gstType = "GOODS"; // GOODS / SERVICES

    @Column(name = "default_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal defaultRate = BigDecimal.valueOf(18);

    @Column(name = "default_uqc", nullable = false, length = 10)
    private String defaultUqc = "OTH";

    @Column(name = "effective_from")
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;
}
