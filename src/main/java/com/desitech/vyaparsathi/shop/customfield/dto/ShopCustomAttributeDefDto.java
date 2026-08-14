package com.desitech.vyaparsathi.shop.customfield.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShopCustomAttributeDefDto {

    public static final Set<String> ALLOWED_FIELD_TYPES =
            Set.of("text", "number", "date", "select", "boolean");

    private Long id;

    /**
     * Machine key used to store the value in
     * {@code item_variant.custom_attributes}. Must be a valid JSON /
     * JavaScript identifier so front-end code can dereference it
     * without quoting: {@code /^[a-zA-Z_][a-zA-Z0-9_]{0,63}$/}.
     */
    @NotBlank(message = "keyName is required")
    @Pattern(regexp = "^[a-zA-Z_][a-zA-Z0-9_]{0,63}$",
             message = "keyName must start with a letter or underscore and contain only alphanumerics or underscores (max 64 chars)")
    private String keyName;

    @NotBlank(message = "label is required")
    @Size(max = 120)
    private String label;

    @NotBlank(message = "fieldType is required")
    @Pattern(regexp = "^(text|number|date|select|boolean)$",
             message = "fieldType must be one of: text, number, date, select, boolean")
    private String fieldType;

    private Boolean required;

    /** For {@code select} fields only. */
    private List<String> options;

    @Size(max = 255)
    private String helpText;

    private Integer displayOrder;

    private Boolean active;
}
