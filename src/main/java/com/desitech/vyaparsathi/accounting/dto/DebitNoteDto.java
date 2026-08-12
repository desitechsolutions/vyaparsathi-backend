package com.desitech.vyaparsathi.accounting.dto;

import com.desitech.vyaparsathi.supplier.dto.SupplierDto;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class DebitNoteDto {
    private Long id;
    private String debitNoteNo;
    private Long purchaseInvoiceId;
    private String purchaseInvoiceNo;
    private SupplierDto supplier;
    private LocalDate debitNoteDate;
    private String reason;
    private BigDecimal taxableAmount;
    private BigDecimal cgstAmount;
    private BigDecimal sgstAmount;
    private BigDecimal igstAmount;
    private BigDecimal totalAmount;
    private BigDecimal appliedAmount;
    private String status;
    private String notes;

    private String signedUrl;
    private Long purchaseReturnId;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getDebitNoteNo() { return debitNoteNo; }
    public void setDebitNoteNo(String debitNoteNo) { this.debitNoteNo = debitNoteNo; }

    public Long getPurchaseInvoiceId() { return purchaseInvoiceId; }
    public void setPurchaseInvoiceId(Long purchaseInvoiceId) { this.purchaseInvoiceId = purchaseInvoiceId; }

    public String getPurchaseInvoiceNo() { return purchaseInvoiceNo; }
    public void setPurchaseInvoiceNo(String purchaseInvoiceNo) { this.purchaseInvoiceNo = purchaseInvoiceNo; }

    public SupplierDto getSupplier() { return supplier; }
    public void setSupplier(SupplierDto supplier) { this.supplier = supplier; }

    public LocalDate getDebitNoteDate() { return debitNoteDate; }
    public void setDebitNoteDate(LocalDate debitNoteDate) { this.debitNoteDate = debitNoteDate; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public BigDecimal getTaxableAmount() { return taxableAmount; }
    public void setTaxableAmount(BigDecimal taxableAmount) { this.taxableAmount = taxableAmount; }

    public BigDecimal getCgstAmount() { return cgstAmount; }
    public void setCgstAmount(BigDecimal cgstAmount) { this.cgstAmount = cgstAmount; }

    public BigDecimal getSgstAmount() { return sgstAmount; }
    public void setSgstAmount(BigDecimal sgstAmount) { this.sgstAmount = sgstAmount; }

    public BigDecimal getIgstAmount() { return igstAmount; }
    public void setIgstAmount(BigDecimal igstAmount) { this.igstAmount = igstAmount; }

    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }

    public BigDecimal getAppliedAmount() { return appliedAmount; }
    public void setAppliedAmount(BigDecimal appliedAmount) { this.appliedAmount = appliedAmount; }

    public String getSignedUrl() { return signedUrl; }
    public void setSignedUrl(String signedUrl) { this.signedUrl = signedUrl; }

    public Long getPurchaseReturnId() { return purchaseReturnId; }
    public void setPurchaseReturnId(Long purchaseReturnId) { this.purchaseReturnId = purchaseReturnId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
