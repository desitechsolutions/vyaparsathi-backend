package com.desitech.vyaparsathi.customer.dto;

import java.math.BigDecimal;

/**
 * Dashboard KPI summary for the Customer list page header.
 * Computed per-shop by {@code CustomerService.getKpis()}.
 */
public class CustomerKpiDto {

    private long totalCustomers;
    private long activeCustomers;
    private long inactiveCustomers;
    private long businessCustomers;
    private long individualCustomers;
    private BigDecimal totalOutstanding = BigDecimal.ZERO;
    private long newThisMonth;

    public long getTotalCustomers() { return totalCustomers; }
    public void setTotalCustomers(long v) { this.totalCustomers = v; }

    public long getActiveCustomers() { return activeCustomers; }
    public void setActiveCustomers(long v) { this.activeCustomers = v; }

    public long getInactiveCustomers() { return inactiveCustomers; }
    public void setInactiveCustomers(long v) { this.inactiveCustomers = v; }

    public long getBusinessCustomers() { return businessCustomers; }
    public void setBusinessCustomers(long v) { this.businessCustomers = v; }

    public long getIndividualCustomers() { return individualCustomers; }
    public void setIndividualCustomers(long v) { this.individualCustomers = v; }

    public BigDecimal getTotalOutstanding() { return totalOutstanding; }
    public void setTotalOutstanding(BigDecimal v) { this.totalOutstanding = v == null ? BigDecimal.ZERO : v; }

    public long getNewThisMonth() { return newThisMonth; }
    public void setNewThisMonth(long v) { this.newThisMonth = v; }
}
