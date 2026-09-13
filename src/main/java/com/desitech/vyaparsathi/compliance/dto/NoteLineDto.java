package com.desitech.vyaparsathi.compliance.dto;

import com.desitech.vyaparsathi.sales.enums.GSTType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

/** One line item on a standalone compliance credit/debit note. */
public class NoteLineDto {

    @NotBlank
    private String     description;
    private String     hsnSac;
    private String     unit;

    @NotNull @Positive
    private BigDecimal qty;

    @NotNull @Positive
    private BigDecimal unitPrice;

    @NotNull
    private GSTType    gstType;

    /** Cess rate in percent (e.g. {@code 1.00} for 1%). Null treated as zero. */
    private BigDecimal cessRate;

    /** Per-line discount amount before tax. Null treated as zero. */
    private BigDecimal lineDiscount;

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getHsnSac() { return hsnSac; }
    public void setHsnSac(String hsnSac) { this.hsnSac = hsnSac; }
    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }
    public BigDecimal getQty() { return qty; }
    public void setQty(BigDecimal qty) { this.qty = qty; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }
    public GSTType getGstType() { return gstType; }
    public void setGstType(GSTType gstType) { this.gstType = gstType; }
    public BigDecimal getCessRate() { return cessRate; }
    public void setCessRate(BigDecimal cessRate) { this.cessRate = cessRate; }
    public BigDecimal getLineDiscount() { return lineDiscount; }
    public void setLineDiscount(BigDecimal lineDiscount) { this.lineDiscount = lineDiscount; }
}
