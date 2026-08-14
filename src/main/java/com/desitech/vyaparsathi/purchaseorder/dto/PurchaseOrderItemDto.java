package com.desitech.vyaparsathi.purchaseorder.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class PurchaseOrderItemDto {
    private Long id;
    private Long itemVariantId;
    private Integer quantity;
    private BigDecimal unitCost;
    private String sku;
    private String name;
    /** V81 — cumulative received qty; used by the FE receipt-progress bar. */
    private BigDecimal receivedQuantity;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getItemVariantId() { return itemVariantId; }
    public void setItemVariantId(Long itemVariantId) { this.itemVariantId = itemVariantId; }

    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }

    public BigDecimal getUnitCost() { return unitCost; }
    public void setUnitCost(BigDecimal unitCost) { this.unitCost = unitCost; }

    public String getSku() { return sku; }
    public void setSku(String sku) { this.sku = sku; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public BigDecimal getReceivedQuantity() { return receivedQuantity; }
    public void setReceivedQuantity(BigDecimal receivedQuantity) { this.receivedQuantity = receivedQuantity; }
}
