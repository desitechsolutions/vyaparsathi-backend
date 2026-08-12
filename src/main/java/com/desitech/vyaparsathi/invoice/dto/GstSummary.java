package com.desitech.vyaparsathi.invoice.dto;

import lombok.Data;

import java.math.BigDecimal;

import static java.math.BigDecimal.ZERO;

@Data
public class GstSummary {
    private final BigDecimal rate;
    private BigDecimal cgst = ZERO;
    private BigDecimal sgst = ZERO;
    private BigDecimal igst = ZERO;
    private BigDecimal utgst = ZERO;

    public GstSummary(BigDecimal rate) { this.rate = rate; }

    public BigDecimal getRate() { return rate; }
    public BigDecimal getCgst() { return cgst; }
    public BigDecimal getSgst() { return sgst; }
    public BigDecimal getIgst() { return igst; }
    public BigDecimal getUtgst() { return utgst; }

    public void addCgst(BigDecimal amt) { if (amt != null) cgst = cgst.add(amt); }
    public void addSgst(BigDecimal amt) { if (amt != null) sgst = sgst.add(amt); }
    public void addIgst(BigDecimal amt) { if (amt != null) igst = igst.add(amt); }
    public void addUtgst(BigDecimal amt) { if (amt != null) utgst = utgst.add(amt); }
}
