package com.desitech.vyaparsathi.gst.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Internal DTO — result of parsing a GSTN GSTR-2B JSON file. Not exposed as API. */
public class Gstr2bLineDto {

    private String supplierGstin;
    private String supplierName;
    private String invoiceNumber;
    private String invoiceType;  // B2B, CDNR
    private LocalDate invoiceDate;
    private BigDecimal invoiceValue;
    private BigDecimal taxableValue;
    private BigDecimal igstAmount;
    private BigDecimal cgstAmount;
    private BigDecimal sgstAmount;
    private BigDecimal cessAmount;
    private String itcAvailability; // Y / N

    public String getSupplierGstin()     { return supplierGstin; }
    public void setSupplierGstin(String v) { this.supplierGstin = v; }

    public String getSupplierName()      { return supplierName; }
    public void setSupplierName(String v)  { this.supplierName = v; }

    public String getInvoiceNumber()     { return invoiceNumber; }
    public void setInvoiceNumber(String v) { this.invoiceNumber = v; }

    public String getInvoiceType()       { return invoiceType; }
    public void setInvoiceType(String v)   { this.invoiceType = v; }

    public LocalDate getInvoiceDate()    { return invoiceDate; }
    public void setInvoiceDate(LocalDate v){ this.invoiceDate = v; }

    public BigDecimal getInvoiceValue()  { return invoiceValue; }
    public void setInvoiceValue(BigDecimal v) { this.invoiceValue = v; }

    public BigDecimal getTaxableValue()  { return taxableValue; }
    public void setTaxableValue(BigDecimal v) { this.taxableValue = v; }

    public BigDecimal getIgstAmount()    { return igstAmount; }
    public void setIgstAmount(BigDecimal v) { this.igstAmount = v; }

    public BigDecimal getCgstAmount()    { return cgstAmount; }
    public void setCgstAmount(BigDecimal v) { this.cgstAmount = v; }

    public BigDecimal getSgstAmount()    { return sgstAmount; }
    public void setSgstAmount(BigDecimal v) { this.sgstAmount = v; }

    public BigDecimal getCessAmount()    { return cessAmount; }
    public void setCessAmount(BigDecimal v) { this.cessAmount = v; }

    public String getItcAvailability()   { return itcAvailability; }
    public void setItcAvailability(String v) { this.itcAvailability = v; }
}
