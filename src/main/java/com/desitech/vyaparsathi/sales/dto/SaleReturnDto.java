package com.desitech.vyaparsathi.sales.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class SaleReturnDto {
    @NotNull
    private Long saleId;
    
    @NotNull
    private List<SaleReturnItemDto> returnItems;
    
    private String reason; // Optional reason for return
    private boolean refundPayment; // Whether to refund payments made

    public Long getSaleId() { return saleId; }
    public void setSaleId(Long saleId) { this.saleId = saleId; }

    public List<SaleReturnItemDto> getReturnItems() { return returnItems; }
    public void setReturnItems(List<SaleReturnItemDto> returnItems) { this.returnItems = returnItems; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public boolean isRefundPayment() { return refundPayment; }
    public void setRefundPayment(boolean refundPayment) { this.refundPayment = refundPayment; }

    @Data
    public static class SaleReturnItemDto {
        @NotNull
        private Long saleItemId;
        
        @NotNull
        private BigDecimal returnQuantity; // Quantity to return (must be <= original quantity)

        public Long getSaleItemId() { return saleItemId; }
        public void setSaleItemId(Long saleItemId) { this.saleItemId = saleItemId; }

        public BigDecimal getReturnQuantity() { return returnQuantity; }
        public void setReturnQuantity(BigDecimal returnQuantity) { this.returnQuantity = returnQuantity; }
    }
}