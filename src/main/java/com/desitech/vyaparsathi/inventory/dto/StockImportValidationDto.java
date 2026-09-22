package com.desitech.vyaparsathi.inventory.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockImportValidationDto {
    private int totalRows;
    private int validRows;
    private int duplicateCount;
    private int errorCount;
    private boolean hasDuplicates;
    @Builder.Default
    private List<StockImportRowPreviewDto> rows = new ArrayList<>();
    @Builder.Default
    private List<String> globalErrors = new ArrayList<>();
}
