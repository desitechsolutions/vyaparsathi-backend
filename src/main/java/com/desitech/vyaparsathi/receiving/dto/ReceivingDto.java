package com.desitech.vyaparsathi.receiving.dto;

import com.desitech.vyaparsathi.supplier.dto.SupplierDto;
import com.desitech.vyaparsathi.receiving.enums.ReceivingStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class ReceivingDto {
    private Long id;
    private String grNumber;

    @NotNull(message = "Purchase Order ID is required")
    private Long purchaseOrderId;
    private String poNumber;
    private ReceivingStatus status;

    private LocalDateTime receivedAt;
    private String receivedBy;

    @Size(max = 500, message = "Notes must not exceed 500 characters")
    private String notes;

    @Valid
    private List<ReceivingItemDto> receivingItems;

    @NotNull(message = "Shop ID is required")
    private Long shopId;

    private String supplierInvoiceNo;
    private java.time.LocalDate supplierInvoiceDate;
    private String vehicleNo;
    private String deliveryChallanNo;
    private Long approvedByUserId;
    private String approvedByUserName;
    private LocalDateTime approvedAt;

    private SupplierDto supplier;
    private Integer putawayQty;
    private String putAwayStatus;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getGrNumber() { return grNumber; }
    public void setGrNumber(String grNumber) { this.grNumber = grNumber; }

    public Long getPurchaseOrderId() { return purchaseOrderId; }
    public void setPurchaseOrderId(Long purchaseOrderId) { this.purchaseOrderId = purchaseOrderId; }

    public String getPoNumber() { return poNumber; }
    public void setPoNumber(String poNumber) { this.poNumber = poNumber; }

    public ReceivingStatus getStatus() { return status; }
    public void setStatus(ReceivingStatus status) { this.status = status; }

    public LocalDateTime getReceivedAt() { return receivedAt; }
    public void setReceivedAt(LocalDateTime receivedAt) { this.receivedAt = receivedAt; }

    public String getReceivedBy() { return receivedBy; }
    public void setReceivedBy(String receivedBy) { this.receivedBy = receivedBy; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public List<ReceivingItemDto> getReceivingItems() { return receivingItems; }
    public void setReceivingItems(List<ReceivingItemDto> receivingItems) { this.receivingItems = receivingItems; }

    public Long getShopId() { return shopId; }
    public void setShopId(Long shopId) { this.shopId = shopId; }

    public String getSupplierInvoiceNo() { return supplierInvoiceNo; }
    public void setSupplierInvoiceNo(String supplierInvoiceNo) { this.supplierInvoiceNo = supplierInvoiceNo; }

    public java.time.LocalDate getSupplierInvoiceDate() { return supplierInvoiceDate; }
    public void setSupplierInvoiceDate(java.time.LocalDate supplierInvoiceDate) { this.supplierInvoiceDate = supplierInvoiceDate; }

    public String getVehicleNo() { return vehicleNo; }
    public void setVehicleNo(String vehicleNo) { this.vehicleNo = vehicleNo; }

    public String getDeliveryChallanNo() { return deliveryChallanNo; }
    public void setDeliveryChallanNo(String deliveryChallanNo) { this.deliveryChallanNo = deliveryChallanNo; }

    public Long getApprovedByUserId() { return approvedByUserId; }
    public void setApprovedByUserId(Long approvedByUserId) { this.approvedByUserId = approvedByUserId; }

    public String getApprovedByUserName() { return approvedByUserName; }
    public void setApprovedByUserName(String approvedByUserName) { this.approvedByUserName = approvedByUserName; }

    public LocalDateTime getApprovedAt() { return approvedAt; }
    public void setApprovedAt(LocalDateTime approvedAt) { this.approvedAt = approvedAt; }

    public SupplierDto getSupplier() { return supplier; }
    public void setSupplier(SupplierDto supplier) { this.supplier = supplier; }

    public Integer getPutawayQty() { return putawayQty; }
    public void setPutawayQty(Integer putawayQty) { this.putawayQty = putawayQty; }

    public String getPutAwayStatus() { return putAwayStatus; }
    public void setPutAwayStatus(String putAwayStatus) { this.putAwayStatus = putAwayStatus; }
}