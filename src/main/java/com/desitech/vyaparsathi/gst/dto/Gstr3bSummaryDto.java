package com.desitech.vyaparsathi.gst.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class Gstr3bSummaryDto {
    private String gstin;
    private String monthYear; // e.g. "04-2025"

    private OutwardSupplies outwardTaxableSupplies = new OutwardSupplies();
    private OutwardSupplies zeroRatedSupplies = new OutwardSupplies();
    private OutwardSupplies nilRatedExemptSupplies = new OutwardSupplies();
    /** Section 3.1(d) — Inward supplies liable to reverse charge (buyer pays tax). */
    private OutwardSupplies inwardReverseChargeSupplies = new OutwardSupplies();

    private ItcDetails itcAvailable = new ItcDetails();
    private ItcDetails itcIneligible = new ItcDetails();

    private TaxPayable netTaxLiability = new TaxPayable();

    public String getGstin() { return gstin; }
    public void setGstin(String gstin) { this.gstin = gstin; }

    public String getMonthYear() { return monthYear; }
    public void setMonthYear(String monthYear) { this.monthYear = monthYear; }

    public OutwardSupplies getOutwardTaxableSupplies() { return outwardTaxableSupplies; }
    public void setOutwardTaxableSupplies(OutwardSupplies outwardTaxableSupplies) { this.outwardTaxableSupplies = outwardTaxableSupplies; }

    public OutwardSupplies getZeroRatedSupplies() { return zeroRatedSupplies; }
    public void setZeroRatedSupplies(OutwardSupplies zeroRatedSupplies) { this.zeroRatedSupplies = zeroRatedSupplies; }

    public OutwardSupplies getNilRatedExemptSupplies() { return nilRatedExemptSupplies; }
    public void setNilRatedExemptSupplies(OutwardSupplies nilRatedExemptSupplies) { this.nilRatedExemptSupplies = nilRatedExemptSupplies; }

    public OutwardSupplies getInwardReverseChargeSupplies() { return inwardReverseChargeSupplies; }
    public void setInwardReverseChargeSupplies(OutwardSupplies inwardReverseChargeSupplies) { this.inwardReverseChargeSupplies = inwardReverseChargeSupplies; }

    public ItcDetails getItcAvailable() { return itcAvailable; }
    public void setItcAvailable(ItcDetails itcAvailable) { this.itcAvailable = itcAvailable; }

    public ItcDetails getItcIneligible() { return itcIneligible; }
    public void setItcIneligible(ItcDetails itcIneligible) { this.itcIneligible = itcIneligible; }

    public TaxPayable getNetTaxLiability() { return netTaxLiability; }
    public void setNetTaxLiability(TaxPayable netTaxLiability) { this.netTaxLiability = netTaxLiability; }

    @Data
    public static class OutwardSupplies {
        private BigDecimal taxableValue = BigDecimal.ZERO;
        private BigDecimal igst = BigDecimal.ZERO;
        private BigDecimal cgst = BigDecimal.ZERO;
        private BigDecimal sgst = BigDecimal.ZERO;
        private BigDecimal utgst = BigDecimal.ZERO;
        private BigDecimal cess = BigDecimal.ZERO;

        public BigDecimal getTaxableValue() { return taxableValue; }
        public void setTaxableValue(BigDecimal taxableValue) { this.taxableValue = taxableValue; }

        public BigDecimal getIgst() { return igst; }
        public void setIgst(BigDecimal igst) { this.igst = igst; }

        public BigDecimal getCgst() { return cgst; }
        public void setCgst(BigDecimal cgst) { this.cgst = cgst; }

        public BigDecimal getSgst() { return sgst; }
        public void setSgst(BigDecimal sgst) { this.sgst = sgst; }

        public BigDecimal getUtgst() { return utgst; }
        public void setUtgst(BigDecimal utgst) { this.utgst = utgst; }

        public BigDecimal getCess() { return cess; }
        public void setCess(BigDecimal cess) { this.cess = cess; }
    }

    @Data
    public static class ItcDetails {
        private BigDecimal igst = BigDecimal.ZERO;
        private BigDecimal cgst = BigDecimal.ZERO;
        private BigDecimal sgst = BigDecimal.ZERO;
        private BigDecimal utgst = BigDecimal.ZERO;
        private BigDecimal cess = BigDecimal.ZERO;

        public BigDecimal getIgst() { return igst; }
        public void setIgst(BigDecimal igst) { this.igst = igst; }

        public BigDecimal getCgst() { return cgst; }
        public void setCgst(BigDecimal cgst) { this.cgst = cgst; }

        public BigDecimal getSgst() { return sgst; }
        public void setSgst(BigDecimal sgst) { this.sgst = sgst; }

        public BigDecimal getUtgst() { return utgst; }
        public void setUtgst(BigDecimal utgst) { this.utgst = utgst; }

        public BigDecimal getCess() { return cess; }
        public void setCess(BigDecimal cess) { this.cess = cess; }
    }

    @Data
    public static class TaxPayable {
        private BigDecimal igstPayable = BigDecimal.ZERO;
        private BigDecimal cgstPayable = BigDecimal.ZERO;
        private BigDecimal sgstPayable = BigDecimal.ZERO;
        private BigDecimal utgstPayable = BigDecimal.ZERO;

        public BigDecimal getIgstPayable() { return igstPayable; }
        public void setIgstPayable(BigDecimal igstPayable) { this.igstPayable = igstPayable; }

        public BigDecimal getCgstPayable() { return cgstPayable; }
        public void setCgstPayable(BigDecimal cgstPayable) { this.cgstPayable = cgstPayable; }

        public BigDecimal getSgstPayable() { return sgstPayable; }
        public void setSgstPayable(BigDecimal sgstPayable) { this.sgstPayable = sgstPayable; }

        public BigDecimal getUtgstPayable() { return utgstPayable; }
        public void setUtgstPayable(BigDecimal utgstPayable) { this.utgstPayable = utgstPayable; }
    }
}
