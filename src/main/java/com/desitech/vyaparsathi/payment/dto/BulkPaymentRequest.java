package com.desitech.vyaparsathi.payment.dto;

import com.desitech.vyaparsathi.payment.enums.PaymentMethod;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class BulkPaymentRequest {
    private Long customerId;
    private BigDecimal totalAmount;
    private PaymentMethod paymentMethod;
    private LocalDateTime paymentDate;
    private String reference;
    private String notes;
    private List<Long> selectedSaleIds;

    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long customerId) { this.customerId = customerId; }

    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }

    public PaymentMethod getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(PaymentMethod paymentMethod) { this.paymentMethod = paymentMethod; }

    public LocalDateTime getPaymentDate() { return paymentDate; }
    public void setPaymentDate(LocalDateTime paymentDate) { this.paymentDate = paymentDate; }

    public String getReference() { return reference; }
    public void setReference(String reference) { this.reference = reference; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public List<Long> getSelectedSaleIds() { return selectedSaleIds; }
    public void setSelectedSaleIds(List<Long> selectedSaleIds) { this.selectedSaleIds = selectedSaleIds; }
}