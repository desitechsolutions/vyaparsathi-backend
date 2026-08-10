package com.desitech.vyaparsathi.einvoice.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class EInvoiceResponseDto {
    private Long saleId;
    private String invoiceNo;
    private String irn; // Invoice Reference Number (64-character hash)
    private String ackNo; // Government Acknowledgment Number
    private LocalDateTime ackDate;
    private String qrCodePath; // Path or Base64 string for QR code printing on invoice
    private String einvoiceStatus; // GENERATED, CANCELLED, FAILED

    public Long getSaleId() { return saleId; }
    public void setSaleId(Long saleId) { this.saleId = saleId; }

    public String getInvoiceNo() { return invoiceNo; }
    public void setInvoiceNo(String invoiceNo) { this.invoiceNo = invoiceNo; }

    public String getIrn() { return irn; }
    public void setIrn(String irn) { this.irn = irn; }

    public String getAckNo() { return ackNo; }
    public void setAckNo(String ackNo) { this.ackNo = ackNo; }

    public LocalDateTime getAckDate() { return ackDate; }
    public void setAckDate(LocalDateTime ackDate) { this.ackDate = ackDate; }

    public String getQrCodePath() { return qrCodePath; }
    public void setQrCodePath(String qrCodePath) { this.qrCodePath = qrCodePath; }

    public String getEinvoiceStatus() { return einvoiceStatus; }
    public void setEinvoiceStatus(String einvoiceStatus) { this.einvoiceStatus = einvoiceStatus; }
}
