package com.desitech.vyaparsathi.reports.dto;

import java.math.BigDecimal;

public class SalesTimeSeriesPointDto {
    private String date;
    private BigDecimal totalSales;
    private int count;

    public SalesTimeSeriesPointDto() {}

    public SalesTimeSeriesPointDto(String date, BigDecimal totalSales, int count) {
        this.date = date;
        this.totalSales = totalSales;
        this.count = count;
    }

    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }

    public BigDecimal getTotalSales() { return totalSales; }
    public void setTotalSales(BigDecimal totalSales) { this.totalSales = totalSales; }

    public int getCount() { return count; }
    public void setCount(int count) { this.count = count; }
}
