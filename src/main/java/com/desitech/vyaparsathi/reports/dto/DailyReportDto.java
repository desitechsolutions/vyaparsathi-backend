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

    private BigDecimal salesTrendPercent;
    private BigDecimal profitTrendPercent;
    private BigDecimal yesterdaySales;
    private BigDecimal yesterdayNetProfit;

    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }

    public BigDecimal getTotalSales() { return totalSales; }
    public void setTotalSales(BigDecimal totalSales) { this.totalSales = totalSales; }

    public int getNumberOfSales() { return numberOfSales; }
    public void setNumberOfSales(int numberOfSales) { this.numberOfSales = numberOfSales; }

    public BigDecimal getTotalExpenses() { return totalExpenses; }
    public void setTotalExpenses(BigDecimal totalExpenses) { this.totalExpenses = totalExpenses; }

    public BigDecimal getTotalPaid() { return totalPaid; }
    public void setTotalPaid(BigDecimal totalPaid) { this.totalPaid = totalPaid; }

    public BigDecimal getNetRevenue() { return netRevenue; }
    public void setNetRevenue(BigDecimal netRevenue) { this.netRevenue = netRevenue; }

    public BigDecimal getTotalCOGS() { return totalCOGS; }
    public void setTotalCOGS(BigDecimal totalCOGS) { this.totalCOGS = totalCOGS; }

    public BigDecimal getNetProfit() { return netProfit; }
    public void setNetProfit(BigDecimal netProfit) { this.netProfit = netProfit; }

    public BigDecimal getOutstandingReceivable() { return outstandingReceivable; }
    public void setOutstandingReceivable(BigDecimal outstandingReceivable) { this.outstandingReceivable = outstandingReceivable; }

    public BigDecimal getSalesTrendPercent() { return salesTrendPercent; }
    public void setSalesTrendPercent(BigDecimal salesTrendPercent) { this.salesTrendPercent = salesTrendPercent; }

    public BigDecimal getProfitTrendPercent() { return profitTrendPercent; }
    public void setProfitTrendPercent(BigDecimal profitTrendPercent) { this.profitTrendPercent = profitTrendPercent; }

    public BigDecimal getYesterdaySales() { return yesterdaySales; }
    public void setYesterdaySales(BigDecimal yesterdaySales) { this.yesterdaySales = yesterdaySales; }

    public BigDecimal getYesterdayNetProfit() { return yesterdayNetProfit; }
    public void setYesterdayNetProfit(BigDecimal yesterdayNetProfit) { this.yesterdayNetProfit = yesterdayNetProfit; }
}