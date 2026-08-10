package com.desitech.vyaparsathi.einvoice.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class EWayBillResponseDto {
    private Long saleId;
    private String invoiceNo;
    private String ewayBillNo; // 12-digit E-Way Bill Number
    private LocalDateTime ewayBillDate;
    private LocalDateTime validUntil;
    private String vehicleNumber;
    private String status; // GENERATED, CANCELLED, EXPIRED

    public Long getSaleId() { return saleId; }
    public void setSaleId(Long saleId) { this.saleId = saleId; }

    public String getInvoiceNo() { return invoiceNo; }
    public void setInvoiceNo(String invoiceNo) { this.invoiceNo = invoiceNo; }

    public String getEwayBillNo() { return ewayBillNo; }
    public void setEwayBillNo(String ewayBillNo) { this.ewayBillNo = ewayBillNo; }

    public LocalDateTime getEwayBillDate() { return ewayBillDate; }
    public void setEwayBillDate(LocalDateTime ewayBillDate) { this.ewayBillDate = ewayBillDate; }

    public LocalDateTime getValidUntil() { return validUntil; }
    public void setValidUntil(LocalDateTime validUntil) { this.validUntil = validUntil; }

    public String getVehicleNumber() { return vehicleNumber; }
    public void setVehicleNumber(String vehicleNumber) { this.vehicleNumber = vehicleNumber; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
