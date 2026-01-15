package com.desitech.vyaparsathi.inventory.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// 3. Update DTO (used for PUT/PATCH /api/categories/{id})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategoryUpdateDto {

    @NotBlank(message = "Category name is required")
    private String name;

    private Long parentId;           // Allow re-parenting (null = make root)
}
