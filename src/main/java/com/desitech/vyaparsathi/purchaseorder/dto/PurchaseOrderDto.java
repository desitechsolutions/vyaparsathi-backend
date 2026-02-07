package com.desitech.vyaparsathi.purchaseorder.dto;

import com.desitech.vyaparsathi.common.util.CustomLocalDateTimeDeserializer;
import com.desitech.vyaparsathi.supplier.dto.SupplierDto;
import com.desitech.vyaparsathi.purchaseorder.enums.PurchaseOrderStatus;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class PurchaseOrderDto {
    private Long id;
    private String poNumber;
    private Long supplierId;
    @JsonDeserialize(using = CustomLocalDateTimeDeserializer.class)
    private LocalDateTime orderDate;
    @JsonDeserialize(using = CustomLocalDateTimeDeserializer.class)
    private LocalDateTime expectedDeliveryDate;
    private BigDecimal totalAmount;
    private PurchaseOrderStatus status;
    private String notes;
    private List<PurchaseOrderItemDto> items;
    private SupplierDto supplier;
}
