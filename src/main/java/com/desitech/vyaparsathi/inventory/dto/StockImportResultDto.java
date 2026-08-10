package com.desitech.vyaparsathi.inventory.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class StockImportResultDto {
    private int totalRows;
    private int successCount;
    private int errorCount;
    private List<String> errors = new ArrayList<>();

    public int getTotalRows() { return totalRows; }
    public void setTotalRows(int totalRows) { this.totalRows = totalRows; }

    public int getSuccessCount() { return successCount; }
    public void setSuccessCount(int successCount) { this.successCount = successCount; }

    public int getErrorCount() { return errorCount; }
    public void setErrorCount(int errorCount) { this.errorCount = errorCount; }

    public List<String> getErrors() { return errors; }
    public void setErrors(List<String> errors) { this.errors = errors; }
}
