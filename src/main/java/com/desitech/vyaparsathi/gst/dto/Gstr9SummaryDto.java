package com.desitech.vyaparsathi.gst.dto;

import java.math.BigDecimal;

/** GSTR-9 Annual Return summary — Tables 4 (outward), 6 (ITC), and 9 (tax paid). */
public class Gstr9SummaryDto {

    // Header
    private String gstin;
    private String tradeName;
    private String fyLabel;           // e.g. "2025-26"
    private int fiscalYear;

    // Table 4 — Outward supplies
    private BigDecimal table4OutwardTaxable = BigDecimal.ZERO;
    private BigDecimal table4ZeroRated      = BigDecimal.ZERO;
    private BigDecimal table4Exempt         = BigDecimal.ZERO;

    // Table 6 — ITC availed
    private BigDecimal table6ItcIgst  = BigDecimal.ZERO;
    private BigDecimal table6ItcCgst  = BigDecimal.ZERO;
    private BigDecimal table6ItcSgst  = BigDecimal.ZERO;

    // Table 9 — Tax payable (from GSTR-1 turnover × rates)
    private BigDecimal table9PayableIgst = BigDecimal.ZERO;
    private BigDecimal table9PayableCgst = BigDecimal.ZERO;
    private BigDecimal table9PayableSgst = BigDecimal.ZERO;

    // Table 9 — Tax paid (from GSTR-3B ITC + cash ledger)
    private BigDecimal table9PaidIgst = BigDecimal.ZERO;
    private BigDecimal table9PaidCgst = BigDecimal.ZERO;
    private BigDecimal table9PaidSgst = BigDecimal.ZERO;

    // Discrepancy (payable − paid)
    private BigDecimal discrepancyIgst = BigDecimal.ZERO;
    private BigDecimal discrepancyCgst = BigDecimal.ZERO;
    private BigDecimal discrepancySgst = BigDecimal.ZERO;

    // Outward tax amounts (from sales)
    private BigDecimal outwardIgst = BigDecimal.ZERO;
    private BigDecimal outwardCgst = BigDecimal.ZERO;
    private BigDecimal outwardSgst = BigDecimal.ZERO;

    public String getGstin()           { return gstin; }
    public void setGstin(String v)       { this.gstin = v; }

    public String getTradeName()       { return tradeName; }
    public void setTradeName(String v)   { this.tradeName = v; }

    public String getFyLabel()         { return fyLabel; }
    public void setFyLabel(String v)     { this.fyLabel = v; }

    public int getFiscalYear()         { return fiscalYear; }
    public void setFiscalYear(int v)     { this.fiscalYear = v; }

    public BigDecimal getTable4OutwardTaxable() { return table4OutwardTaxable; }
    public void setTable4OutwardTaxable(BigDecimal v) { this.table4OutwardTaxable = v; }

    public BigDecimal getTable4ZeroRated() { return table4ZeroRated; }
    public void setTable4ZeroRated(BigDecimal v) { this.table4ZeroRated = v; }

    public BigDecimal getTable4Exempt()  { return table4Exempt; }
    public void setTable4Exempt(BigDecimal v) { this.table4Exempt = v; }

    public BigDecimal getTable6ItcIgst() { return table6ItcIgst; }
    public void setTable6ItcIgst(BigDecimal v) { this.table6ItcIgst = v; }

    public BigDecimal getTable6ItcCgst() { return table6ItcCgst; }
    public void setTable6ItcCgst(BigDecimal v) { this.table6ItcCgst = v; }

    public BigDecimal getTable6ItcSgst() { return table6ItcSgst; }
    public void setTable6ItcSgst(BigDecimal v) { this.table6ItcSgst = v; }

    public BigDecimal getTable9PayableIgst() { return table9PayableIgst; }
    public void setTable9PayableIgst(BigDecimal v) { this.table9PayableIgst = v; }

    public BigDecimal getTable9PayableCgst() { return table9PayableCgst; }
    public void setTable9PayableCgst(BigDecimal v) { this.table9PayableCgst = v; }

    public BigDecimal getTable9PayableSgst() { return table9PayableSgst; }
    public void setTable9PayableSgst(BigDecimal v) { this.table9PayableSgst = v; }

    public BigDecimal getTable9PaidIgst() { return table9PaidIgst; }
    public void setTable9PaidIgst(BigDecimal v) { this.table9PaidIgst = v; }

    public BigDecimal getTable9PaidCgst() { return table9PaidCgst; }
    public void setTable9PaidCgst(BigDecimal v) { this.table9PaidCgst = v; }

    public BigDecimal getTable9PaidSgst() { return table9PaidSgst; }
    public void setTable9PaidSgst(BigDecimal v) { this.table9PaidSgst = v; }

    public BigDecimal getDiscrepancyIgst() { return discrepancyIgst; }
    public void setDiscrepancyIgst(BigDecimal v) { this.discrepancyIgst = v; }

    public BigDecimal getDiscrepancyCgst() { return discrepancyCgst; }
    public void setDiscrepancyCgst(BigDecimal v) { this.discrepancyCgst = v; }

    public BigDecimal getDiscrepancySgst() { return discrepancySgst; }
    public void setDiscrepancySgst(BigDecimal v) { this.discrepancySgst = v; }

    public BigDecimal getOutwardIgst() { return outwardIgst; }
    public void setOutwardIgst(BigDecimal v) { this.outwardIgst = v; }

    public BigDecimal getOutwardCgst() { return outwardCgst; }
    public void setOutwardCgst(BigDecimal v) { this.outwardCgst = v; }

    public BigDecimal getOutwardSgst() { return outwardSgst; }
    public void setOutwardSgst(BigDecimal v) { this.outwardSgst = v; }
}
