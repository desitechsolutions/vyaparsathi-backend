package com.desitech.vyaparsathi.sales.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class InvoiceItemDto {
    private Long id;
    private String itemName;
    private String sku;
    private Integer quantity;
    private BigDecimal price;
    private BigDecimal total;
}
