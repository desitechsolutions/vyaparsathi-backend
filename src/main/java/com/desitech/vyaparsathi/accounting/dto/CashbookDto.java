package com.desitech.vyaparsathi.accounting.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Data
public class CashbookDto {
    private LocalDate date;
    private BigDecimal openingCashBalance = BigDecimal.ZERO;
    private BigDecimal totalCashInflow = BigDecimal.ZERO;
    private BigDecimal totalCashOutflow = BigDecimal.ZERO;
    private BigDecimal closingCashBalance = BigDecimal.ZERO;

    private List<CashTransaction> transactions = new ArrayList<>();

    @Data
    public static class CashTransaction {
        private String time;
        private String type; // INFLOW, OUTFLOW
        private String category; // SALE, VENDOR_PAYMENT, EXPENSE, CASH_DEPOSIT
        private BigDecimal amount;
        private String referenceNo;
        private String description;

        public String getTime() { return time; }
        public void setTime(String time) { this.time = time; }

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }

        public String getCategory() { return category; }
        public void setCategory(String category) { this.category = category; }

        public BigDecimal getAmount() { return amount; }
        public void setAmount(BigDecimal amount) { this.amount = amount; }

        public String getReferenceNo() { return referenceNo; }
        public void setReferenceNo(String referenceNo) { this.referenceNo = referenceNo; }

        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
    }

    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }

    public BigDecimal getOpeningCashBalance() { return openingCashBalance; }
    public void setOpeningCashBalance(BigDecimal openingCashBalance) { this.openingCashBalance = openingCashBalance; }

    public BigDecimal getTotalCashInflow() { return totalCashInflow; }
    public void setTotalCashInflow(BigDecimal totalCashInflow) { this.totalCashInflow = totalCashInflow; }

    public BigDecimal getTotalCashOutflow() { return totalCashOutflow; }
    public void setTotalCashOutflow(BigDecimal totalCashOutflow) { this.totalCashOutflow = totalCashOutflow; }

    public BigDecimal getClosingCashBalance() { return closingCashBalance; }
    public void setClosingCashBalance(BigDecimal closingCashBalance) { this.closingCashBalance = closingCashBalance; }

    public List<CashTransaction> getTransactions() { return transactions; }
    public void setTransactions(List<CashTransaction> transactions) { this.transactions = transactions; }
}
