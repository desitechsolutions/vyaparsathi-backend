package com.desitech.vyaparsathi.gst.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** API response DTO — one reconciliation line. */
public class Gstr2bEntryDto {

    private Long id;
    private String supplierGstin;
    private String supplierName;
    private String invoiceNumber;
    private String invoiceType;
    private LocalDate invoiceDate;
    private BigDecimal invoiceValue;
    private BigDecimal taxableValue;
    private BigDecimal portalIgst;
    private BigDecimal portalCgst;
    private BigDecimal portalSgst;
    private BigDecimal booksIgst;
    private BigDecimal booksCgst;
    private BigDecimal booksSgst;
    private BigDecimal taxDifference;
    private String itcAvailability;
    private String matchStatus;
    private Long matchedPurchaseId;
    private String matchedPurchaseInvoiceNo;

    public Long getId()                  { return id; }
    public void setId(Long v)              { this.id = v; }

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

    public BigDecimal getPortalIgst()    { return portalIgst; }
    public void setPortalIgst(BigDecimal v) { this.portalIgst = v; }

    public BigDecimal getPortalCgst()    { return portalCgst; }
    public void setPortalCgst(BigDecimal v) { this.portalCgst = v; }

    public BigDecimal getPortalSgst()    { return portalSgst; }
    public void setPortalSgst(BigDecimal v) { this.portalSgst = v; }

    public BigDecimal getBooksIgst()     { return booksIgst; }
    public void setBooksIgst(BigDecimal v) { this.booksIgst = v; }

    public BigDecimal getBooksCgst()     { return booksCgst; }
    public void setBooksCgst(BigDecimal v) { this.booksCgst = v; }

    public BigDecimal getBooksSgst()     { return booksSgst; }
    public void setBooksSgst(BigDecimal v) { this.booksSgst = v; }

    public BigDecimal getTaxDifference() { return taxDifference; }
    public void setTaxDifference(BigDecimal v) { this.taxDifference = v; }

    public String getItcAvailability()   { return itcAvailability; }
    public void setItcAvailability(String v) { this.itcAvailability = v; }

    public String getMatchStatus()       { return matchStatus; }
    public void setMatchStatus(String v)   { this.matchStatus = v; }

    public Long getMatchedPurchaseId()   { return matchedPurchaseId; }
    public void setMatchedPurchaseId(Long v) { this.matchedPurchaseId = v; }

    public String getMatchedPurchaseInvoiceNo() { return matchedPurchaseInvoiceNo; }
    public void setMatchedPurchaseInvoiceNo(String v) { this.matchedPurchaseInvoiceNo = v; }
}
