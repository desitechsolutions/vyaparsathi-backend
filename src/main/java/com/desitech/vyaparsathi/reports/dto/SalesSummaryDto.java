package com.desitech.vyaparsathi.reports.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Schema(description = "Sales summary report with corrected financial calculations")
public class SalesSummaryDto {
    @Schema(description = "Report start date", example = "2024-01-01")
    private LocalDate fromDate;
    
    @Schema(description = "Report end date", example = "2024-01-31")
    private LocalDate toDate;
    
    @Schema(description = "Total sales amount including taxes", example = "150000.00")
    private BigDecimal totalSales;
    
    @Schema(description = "Number of sales transactions", example = "45")
    private int totalSalesCount;
    
    @Schema(description = "Total taxable value (excluding GST)", example = "127118.64")
    private BigDecimal totalTaxableValue;
    
    @Schema(description = "Total GST amount collected", example = "22881.36")
    private BigDecimal totalGstAmount;
    
    @Schema(description = "Total round-off adjustments", example = "15.50")
    private BigDecimal totalRoundOff;
    
    @Schema(description = "Total amount actually paid by customers", example = "120000.00")
    private BigDecimal totalPaid;
    
    @Schema(description = "Net revenue = Total sales minus returns/discounts (if any)", example = "150000.00")
    private BigDecimal netRevenue; 
    
    @Schema(description = "Total Cost of Goods Sold calculated from purchase costs", example = "90000.00")
    private BigDecimal totalCOGS;
    
    @Schema(description = "Net profit = Net revenue minus COGS minus operational expenses (excludes inventory purchases)", example = "40000.00")
    private BigDecimal netProfit;
    
    @Schema(description = "Outstanding receivables = Total sales minus total paid", example = "30000.00")
    private BigDecimal outstandingReceivable;

    // Previous period comparison
    private LocalDate previousFromDate;
    private LocalDate previousToDate;
    private BigDecimal previousTotalSales;
    private BigDecimal previousNetProfit;

    // Growth percentages
    private BigDecimal salesGrowthPercent;
    private BigDecimal profitGrowthPercent;
}