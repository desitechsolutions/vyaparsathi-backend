package com.desitech.vyaparsathi.inventory.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Summary result returned after processing a stock import (Excel/CSV).
 */
@Data
public class StockImportResultDto {
    private int totalRows;
    private int successCount;
    private int errorCount;
    private List<String> errors = new ArrayList<>();
}
