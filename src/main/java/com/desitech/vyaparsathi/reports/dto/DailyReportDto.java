package com.desitech.vyaparsathi.reports.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Schema(description = "Daily financial report with corrected profit/loss calculations")
public class DailyReportDto {
    @Schema(description = "Report date", example = "2024-01-15")
    private LocalDate date;
    
    @Schema(description = "Total sales amount for the day", example = "5000.00")
    private BigDecimal totalSales;
    
    @Schema(description = "Number of sales transactions", example = "12")
    private int numberOfSales;
    
    @Schema(description = "Total operational expenses (excludes inventory purchases)", example = "800.00")
    private BigDecimal totalExpenses;
    
    @Schema(description = "Total amount paid by customers", example = "4200.00")
    private BigDecimal totalPaid;
    
    @Schema(description = "Net revenue = Total sales minus returns/discounts (if any)", example = "5000.00")
    private BigDecimal netRevenue;
    
    @Schema(description = "Total Cost of Goods Sold for items sold today", example = "3000.00")
    private BigDecimal totalCOGS;
    
    @Schema(description = "Net profit = Net revenue minus COGS minus operational expenses", example = "1200.00")
    private BigDecimal netProfit;
    
    @Schema(description = "Outstanding receivables = Total sales minus total paid", example = "800.00")
    private BigDecimal outstandingReceivable;

    private BigDecimal salesTrendPercent;      // % change vs yesterday
    private BigDecimal profitTrendPercent;     // % change vs yesterday
    private BigDecimal yesterdaySales;         // optional - for transparency
    private BigDecimal yesterdayNetProfit;     // optional
}