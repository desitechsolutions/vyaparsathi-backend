package com.desitech.vyaparsathi.accounting.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class PayablesAgingDto {
    private Long supplierId;
    private String supplierName;
    private String phone;
    private BigDecimal current0To30Days = BigDecimal.ZERO;
    private BigDecimal days31To60 = BigDecimal.ZERO;
    private BigDecimal days61To90 = BigDecimal.ZERO;
    private BigDecimal over90Days = BigDecimal.ZERO;
    private BigDecimal totalPayable = BigDecimal.ZERO;

    public Long getSupplierId() { return supplierId; }
    public void setSupplierId(Long supplierId) { this.supplierId = supplierId; }

    public String getSupplierName() { return supplierName; }
    public void setSupplierName(String supplierName) { this.supplierName = supplierName; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public BigDecimal getCurrent0To30Days() { return current0To30Days; }
    public void setCurrent0To30Days(BigDecimal current0To30Days) { this.current0To30Days = current0To30Days; }

    public BigDecimal getDays31To60() { return days31To60; }
    public void setDays31To60(BigDecimal days31To60) { this.days31To60 = days31To60; }

    public BigDecimal getDays61To90() { return days61To90; }
    public void setDays61To90(BigDecimal days61To90) { this.days61To90 = days61To90; }

    public BigDecimal getOver90Days() { return over90Days; }
    public void setOver90Days(BigDecimal over90Days) { this.over90Days = over90Days; }

    public BigDecimal getTotalPayable() { return totalPayable; }
    public void setTotalPayable(BigDecimal totalPayable) { this.totalPayable = totalPayable; }
}
