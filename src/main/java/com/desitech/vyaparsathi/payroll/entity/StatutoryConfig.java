package com.desitech.vyaparsathi.payroll.entity;

import lombok.*;
import jakarta.persistence.*;
import com.desitech.vyaparsathi.common.entities.ShopAwareEntity;

@Entity
@Table(name = "statutory_config")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StatutoryConfig extends ShopAwareEntity {
    @Column(length = 50)
    private String pfUan;

    @Column(length = 50)
    private String esicCode;

    @Column(length = 2)
    private String ptState;

    @Column(length = 50)
    private String taxRegime;

    @Column(length = 100)
    private String bankName;

    @Column(length = 20)
    private String bankAccountNumber;

    @Column(length = 11)
    private String bankIfsc;

    @Column(length = 100)
    private String bankBranch;

    @Column(length = 255)
    private String razorpayxApiKey;

    @Column(length = 255)
    private String razorpayxApiSecret;

    @Column(length = 100)
    private String razorpayxAccountId;
}
