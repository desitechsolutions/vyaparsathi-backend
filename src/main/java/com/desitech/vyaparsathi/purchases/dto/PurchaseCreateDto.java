package com.desitech.vyaparsathi.purchases.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
public class PurchaseCreateDto {
    @NotNull(message = "Supplier ID is required")
    private Long supplierId;

    private String supplierInvoiceNo;
    private LocalDate purchaseDate;
    private String paymentTerms = "NET_30";
    private String notes;

    private Boolean isInterState = false;

    private BigDecimal initialPaymentAmount = BigDecimal.ZERO;
    private String paymentMethod = "CASH";

    @NotNull(message = "Items list cannot be empty")
    private List<PurchaseItemCreateDto> items;

    public Long getSupplierId() { return supplierId; }
    public void setSupplierId(Long supplierId) { this.supplierId = supplierId; }

    public String getSupplierInvoiceNo() { return supplierInvoiceNo; }
    public void setSupplierInvoiceNo(String supplierInvoiceNo) { this.supplierInvoiceNo = supplierInvoiceNo; }

    public LocalDate getPurchaseDate() { return purchaseDate; }
    public void setPurchaseDate(LocalDate purchaseDate) { this.purchaseDate = purchaseDate; }

    public String getPaymentTerms() { return paymentTerms; }
    public void setPaymentTerms(String paymentTerms) { this.paymentTerms = paymentTerms; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public Boolean getIsInterState() { return isInterState; }
    public void setIsInterState(Boolean isInterState) { this.isInterState = isInterState; }

    public BigDecimal getInitialPaymentAmount() { return initialPaymentAmount; }
    public void setInitialPaymentAmount(BigDecimal initialPaymentAmount) { this.initialPaymentAmount = initialPaymentAmount; }

    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }

    public List<PurchaseItemCreateDto> getItems() { return items; }
    public void setItems(List<PurchaseItemCreateDto> items) { this.items = items; }

    @Data
    public static class PurchaseItemCreateDto {
        @NotNull(message = "Item variant ID is required")
        private Long itemVariantId;

        private String batchNumber;
        private LocalDate expiryDate;

        @NotNull(message = "Quantity is required")
        private BigDecimal quantity;

        @NotNull(message = "Unit cost is required")
        private BigDecimal unitCost;

        private BigDecimal discount = BigDecimal.ZERO;
        private BigDecimal gstRate = BigDecimal.ZERO;

        public Long getItemVariantId() { return itemVariantId; }
        public void setItemVariantId(Long itemVariantId) { this.itemVariantId = itemVariantId; }

        public String getBatchNumber() { return batchNumber; }
        public void setBatchNumber(String batchNumber) { this.batchNumber = batchNumber; }

        public LocalDate getExpiryDate() { return expiryDate; }
        public void setExpiryDate(LocalDate expiryDate) { this.expiryDate = expiryDate; }

        public BigDecimal getQuantity() { return quantity; }
        public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }

        public BigDecimal getUnitCost() { return unitCost; }
        public void setUnitCost(BigDecimal unitCost) { this.unitCost = unitCost; }

        public BigDecimal getDiscount() { return discount; }
        public void setDiscount(BigDecimal discount) { this.discount = discount; }

        public BigDecimal getGstRate() { return gstRate; }
        public void setGstRate(BigDecimal gstRate) { this.gstRate = gstRate; }
    }
}
