package com.desitech.vyaparsathi.expense.dto;

import com.desitech.vyaparsathi.common.util.CustomLocalDateTimeDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Update Expense DTO (Enterprise)
 *
 * Allows updating expense fields (only allowed in DRAFT status)
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateExpenseDto {
    // ── Legacy fields ────────────────────────────────────────────────
    @NotBlank(message = "Expense type cannot be blank")
    private String type;
    @NotNull(message = "Amount cannot be null")
    @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
    private BigDecimal amount;
    @JsonDeserialize(using = CustomLocalDateTimeDeserializer.class)
    private LocalDateTime date;
    private String notes;

    // ── Enterprise fields ────────────────────────────────────────────
    private Long expenseCategoryId;
    private String vendorName;
    private Long vendorId;
    private LocalDate expenseDate;
    private String paymentMethod;
    private String currency;
    private String description;
    private String costCenter;
    private List<String> tags;
    private Long receiptId;
}