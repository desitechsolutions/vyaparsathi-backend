package com.desitech.vyaparsathi.payroll.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@EqualsAndHashCode(callSuper = true)
public class StaffResponseDto extends StaffDto {
    private boolean isPaidInCurrentPeriod;
    private LocalDate lastPaymentDate;
    private BigDecimal lastPaidAmount;

    public boolean isPaidInCurrentPeriod() { return isPaidInCurrentPeriod; }
    public void setPaidInCurrentPeriod(boolean isPaidInCurrentPeriod) { this.isPaidInCurrentPeriod = isPaidInCurrentPeriod; }

    public LocalDate getLastPaymentDate() { return lastPaymentDate; }
    public void setLastPaymentDate(LocalDate lastPaymentDate) { this.lastPaymentDate = lastPaymentDate; }

    public BigDecimal getLastPaidAmount() { return lastPaidAmount; }
    public void setLastPaidAmount(BigDecimal lastPaidAmount) { this.lastPaidAmount = lastPaidAmount; }
}