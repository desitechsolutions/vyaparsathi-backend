package com.desitech.vyaparsathi.customer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CustomerCustomFieldValueDto {
    private Long id;
    private Long customerId;

    @NotBlank
    @Size(max = 80)
    private String fieldKey;

    private String fieldValue;
}
