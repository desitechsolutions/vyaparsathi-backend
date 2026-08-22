package com.desitech.vyaparsathi.payroll.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StatutoryConfigDto {
    private Long id;
    private String pfUan;
    private String esicCode;
    private String ptState;
    private String taxRegime;
    private String bankName;
    private String bankAccountNumber;
    private String bankIfsc;
    private String bankBranch;
}
