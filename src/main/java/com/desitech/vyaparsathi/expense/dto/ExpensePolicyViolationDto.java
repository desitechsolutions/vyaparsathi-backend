package com.desitech.vyaparsathi.expense.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExpensePolicyViolationDto {
    private Long policyId;
    private String policyName;
    private String reason;
    private String enforcementAction; // AUTO_REJECT, ESCALATE, FLAG_FOR_REVIEW, WARN_ONLY
    private String ruleType; // AMOUNT_LIMIT, REQUIRES_RECEIPT, CATEGORY_RESTRICTION, FREQUENCY_LIMIT
}
