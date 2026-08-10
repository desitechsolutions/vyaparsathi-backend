package com.desitech.vyaparsathi.delivery.dto;

import com.desitech.vyaparsathi.delivery.enums.DeliveryPaidBy;
import com.desitech.vyaparsathi.delivery.enums.DeliveryStatus;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class DeliveryDTO {
    private Long id;
    private Long saleId;
    private String invoiceNumber;
    private String customerName;
    private String deliveryAddress;
    private Double deliveryCharge;
    private DeliveryPaidBy deliveryPaidBy;
    private DeliveryStatus deliveryStatus;
    private DeliveryPersonDTO deliveryPerson;
    private String deliveryNotes;
    private LocalDateTime deliveredAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<DeliveryStatusHistoryDTO> statusHistory;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getSaleId() { return saleId; }
    public void setSaleId(Long saleId) { this.saleId = saleId; }

    public String getInvoiceNumber() { return invoiceNumber; }
    public void setInvoiceNumber(String invoiceNumber) { this.invoiceNumber = invoiceNumber; }

    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }

    public String getDeliveryAddress() { return deliveryAddress; }
    public void setDeliveryAddress(String deliveryAddress) { this.deliveryAddress = deliveryAddress; }

    public Double getDeliveryCharge() { return deliveryCharge; }
    public void setDeliveryCharge(Double deliveryCharge) { this.deliveryCharge = deliveryCharge; }

    public DeliveryPaidBy getDeliveryPaidBy() { return deliveryPaidBy; }
    public void setDeliveryPaidBy(DeliveryPaidBy deliveryPaidBy) { this.deliveryPaidBy = deliveryPaidBy; }

    public DeliveryStatus getDeliveryStatus() { return deliveryStatus; }
    public void setDeliveryStatus(DeliveryStatus deliveryStatus) { this.deliveryStatus = deliveryStatus; }

    public DeliveryPersonDTO getDeliveryPerson() { return deliveryPerson; }
    public void setDeliveryPerson(DeliveryPersonDTO deliveryPerson) { this.deliveryPerson = deliveryPerson; }

    public String getDeliveryNotes() { return deliveryNotes; }
    public void setDeliveryNotes(String deliveryNotes) { this.deliveryNotes = deliveryNotes; }

    public LocalDateTime getDeliveredAt() { return deliveredAt; }
    public void setDeliveredAt(LocalDateTime deliveredAt) { this.deliveredAt = deliveredAt; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public List<DeliveryStatusHistoryDTO> getStatusHistory() { return statusHistory; }
    public void setStatusHistory(List<DeliveryStatusHistoryDTO> statusHistory) { this.statusHistory = statusHistory; }
}