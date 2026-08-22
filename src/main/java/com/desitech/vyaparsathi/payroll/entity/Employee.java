package com.desitech.vyaparsathi.payroll.entity;

import com.desitech.vyaparsathi.common.entities.ShopAwareEntity;
import com.desitech.vyaparsathi.payroll.enums.EmploymentStatus;
import com.desitech.vyaparsathi.payroll.enums.EmploymentType;
import com.desitech.vyaparsathi.payroll.enums.PaymentPreference;
import com.desitech.vyaparsathi.payroll.enums.TaxRegime;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "employees", indexes = {
        @Index(name = "idx_shop_status", columnList = "shop_id,employment_status"),
        @Index(name = "idx_shop_active", columnList = "shop_id,is_active")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Employee extends ShopAwareEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String employeeCode;

    @Column(nullable = false, length = 100)
    private String firstName;

    @Column(length = 100)
    private String lastName;

    @Column(length = 150)
    private String email;

    @Column(nullable = false, length = 20)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "ENUM('MALE','FEMALE','OTHER') DEFAULT 'MALE'")
    private String gender = "MALE";

    private LocalDate dateOfBirth;

    @Column(nullable = false)
    private LocalDate joiningDate;

    private LocalDate exitDate;

    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "ENUM('ACTIVE','PROBATION','NOTICE_PERIOD','TERMINATED','RESIGNED') DEFAULT 'ACTIVE'")
    private EmploymentStatus employmentStatus = EmploymentStatus.ACTIVE;

    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "ENUM('FULL_TIME','PART_TIME','CONTRACTOR','INTERN') DEFAULT 'FULL_TIME'")
    private EmploymentType employmentType = EmploymentType.FULL_TIME;

    @Column(length = 100)
    private String department;

    @Column(length = 100)
    private String designation;

    private Long reportingTo;

    // Banking & Payout Details
    @Column(length = 50)
    private String bankAccountNumber;

    @Column(length = 20)
    private String bankIFSCCode;

    @Column(length = 100)
    private String bankName;

    @Column(length = 100)
    private String bankBranch;

    @Column(length = 100)
    private String bankBeneficiaryName;

    @Column(length = 100)
    private String upiId;

    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "ENUM('BANK_TRANSFER','UPI','CASH','CHEQUE') DEFAULT 'BANK_TRANSFER'")
    private PaymentPreference paymentPreference = PaymentPreference.BANK_TRANSFER;

    // Statutory & Tax KYC
    @Column(length = 10, unique = true)
    private String panNumber;

    @Column(length = 12)
    private String aadhaarNumber;

    @Column(length = 12)
    private String uanNumber;

    @Column(columnDefinition = "BOOLEAN DEFAULT FALSE")
    private Boolean pfEnrolled = false;

    @Column(length = 17)
    private String esicNumber;

    @Column(columnDefinition = "BOOLEAN DEFAULT FALSE")
    private Boolean esicEnrolled = false;

    @Column(length = 50)
    private String ptState;

    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "ENUM('NEW_REGIME','OLD_REGIME') DEFAULT 'NEW_REGIME'")
    private TaxRegime taxRegime = TaxRegime.NEW_REGIME;

    // Salary Structure Reference
    @Column(name = "salary_structure_id")
    private Long salaryStructureId;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal monthlyCTC = BigDecimal.ZERO;

    @Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT TRUE")
    private Boolean isActive = true;
}
