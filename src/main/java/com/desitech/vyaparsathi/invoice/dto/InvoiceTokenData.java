package com.desitech.vyaparsathi.invoice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class InvoiceTokenData {
    public Long saleId;
    public String invoiceNo;
    public Long shopId;

    public InvoiceTokenData(Long saleId, String invoiceNo) {
        this.saleId = saleId;
        this.invoiceNo = invoiceNo;
        this.shopId = null;
    }

    public Long getSaleId() { return saleId; }
    public void setSaleId(Long saleId) { this.saleId = saleId; }

    public String getInvoiceNo() { return invoiceNo; }
    public void setInvoiceNo(String invoiceNo) { this.invoiceNo = invoiceNo; }

    public Long getShopId() { return shopId; }
    public void setShopId(Long shopId) { this.shopId = shopId; }
}
