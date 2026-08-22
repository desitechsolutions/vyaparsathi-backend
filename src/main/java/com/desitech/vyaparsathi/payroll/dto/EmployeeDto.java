package com.desitech.vyaparsathi.payroll.dto;

import com.desitech.vyaparsathi.payroll.enums.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmployeeDto {

    private Long id;

    @NotBlank(message = "Employee code is required")
    private String employeeCode;

    @NotBlank(message = "First name is required")
    private String firstName;

    private String lastName;

    @Email(message = "Email should be valid")
    private String email;

    @NotBlank(message = "Phone is required")
    @Pattern(regexp = "^\\d{10}$", message = "Phone must be 10 digits")
    private String phone;

    private String gender;

    private LocalDate dateOfBirth;

    @NotNull(message = "Joining date is required")
    private LocalDate joiningDate;

    private LocalDate exitDate;

    private EmploymentStatus employmentStatus;

    private EmploymentType employmentType;

    private String department;

    private String designation;

    private Long reportingTo;

    // Banking Details
    private String bankAccountNumber;

    private String bankIFSCCode;

    private String bankName;

    private String bankBranch;

    private String bankBeneficiaryName;

    private String upiId;

    private PaymentPreference paymentPreference;

    // KYC & Statutory
    @Pattern(regexp = "^[A-Z]{5}[0-9]{4}[A-Z]{1}$", message = "Invalid PAN format")
    private String panNumber;

    @Pattern(regexp = "^[0-9]{12}$", message = "Aadhaar must be 12 digits")
    private String aadhaarNumber;

    private String uanNumber;

    private Boolean pfEnrolled;

    private String esicNumber;

    private Boolean esicEnrolled;

    private String ptState;

    private TaxRegime taxRegime;

    // Compensation
    private Long salaryStructureId;

    private BigDecimal monthlyCTC;

    private Boolean isActive;
}
