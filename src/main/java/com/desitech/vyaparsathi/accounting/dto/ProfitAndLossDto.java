package com.desitech.vyaparsathi.accounting.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class ProfitAndLossDto {
    private LocalDate startDate;
    private LocalDate endDate;

    private BigDecimal grossSales = BigDecimal.ZERO;
    private BigDecimal salesReturns = BigDecimal.ZERO;
    private BigDecimal netSales = BigDecimal.ZERO;

    private BigDecimal costOfGoodsSold = BigDecimal.ZERO; // COGS from Stock Movements
    private BigDecimal grossProfit = BigDecimal.ZERO;

    private BigDecimal operatingExpenses = BigDecimal.ZERO;
    private BigDecimal netProfit = BigDecimal.ZERO;
    private Double netProfitMarginPercent = 0.0;

    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }

    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }

    public BigDecimal getGrossSales() { return grossSales; }
    public void setGrossSales(BigDecimal grossSales) { this.grossSales = grossSales; }

    public BigDecimal getSalesReturns() { return salesReturns; }
    public void setSalesReturns(BigDecimal salesReturns) { this.salesReturns = salesReturns; }

    public BigDecimal getNetSales() { return netSales; }
    public void setNetSales(BigDecimal netSales) { this.netSales = netSales; }

    public BigDecimal getCostOfGoodsSold() { return costOfGoodsSold; }
    public void setCostOfGoodsSold(BigDecimal costOfGoodsSold) { this.costOfGoodsSold = costOfGoodsSold; }

    public BigDecimal getGrossProfit() { return grossProfit; }
    public void setGrossProfit(BigDecimal grossProfit) { this.grossProfit = grossProfit; }

    public BigDecimal getOperatingExpenses() { return operatingExpenses; }
    public void setOperatingExpenses(BigDecimal operatingExpenses) { this.operatingExpenses = operatingExpenses; }

    public BigDecimal getNetProfit() { return netProfit; }
    public void setNetProfit(BigDecimal netProfit) { this.netProfit = netProfit; }

    public Double getNetProfitMarginPercent() { return netProfitMarginPercent; }
    public void setNetProfitMarginPercent(Double netProfitMarginPercent) { this.netProfitMarginPercent = netProfitMarginPercent; }
}
