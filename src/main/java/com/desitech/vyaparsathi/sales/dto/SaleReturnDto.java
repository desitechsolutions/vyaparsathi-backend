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

    @Data
    public static class SaleReturnItemDto {
        @NotNull
        private Long saleItemId;
        
        @NotNull
        private BigDecimal returnQuantity; // Quantity to return (must be <= original quantity)
    }
}