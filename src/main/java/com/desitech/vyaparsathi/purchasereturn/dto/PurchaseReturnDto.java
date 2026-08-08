package com.desitech.vyaparsathi.purchasereturn.dto;

import com.desitech.vyaparsathi.purchasereturn.enums.PurchaseReturnStatus;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class PurchaseReturnDto {
    private Long id;
    private String returnNo;
    private Long supplierId;
    private String supplierName;
    private Long purchaseOrderId;
    private String poNumber;
    private Long receivingId;
    private LocalDateTime returnDate;
    private BigDecimal totalAmount;
    private PurchaseReturnStatus status;
    private String notes;
    private List<PurchaseReturnItemDto> items;
    private com.desitech.vyaparsathi.supplier.dto.SupplierDto supplier;
    private LocalDateTime createdAt;
}
