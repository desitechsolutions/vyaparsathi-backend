package com.desitech.vyaparsathi.gst.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

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

    /** Section 3.1(b) — zero-rated outward supplies (exports, SEZ). */
    private OutwardSupplies zeroRatedExportSupplies = new OutwardSupplies();

    /** Section 3.1(e) — non-GST outward supplies. */
    private OutwardSupplies nonGstSupplies = new OutwardSupplies();

    /** Table 3.2 — inter-state supplies to unregistered persons, grouped by POS. */
    private List<InterStatePosEntry> interStateUnregistered = new ArrayList<>();

    /** Section 4(A)(3) — ITC on inward RCM supplies (ISRC). */
    private ItcDetails itcRcm = new ItcDetails();

    /** Section 4(A)(5) — other eligible ITC: inputs, capital goods, services (non-RCM). */
    private ItcDetails itcOther = new ItcDetails();

    /** Section 4(B) — ITC reversed (zero for initial implementation). */
    private ItcDetails itcReversed = new ItcDetails();

    /** Section 4(C) — Net ITC = 4(A) − 4(B). */
    private ItcDetails itcNet = new ItcDetails();

    /**
     * Section 6 — ITC offset summary (Rule 88A/88B).
     * Shows how much of each head's liability was settled through ITC (cross-head
     * or same-head) versus cash, and the closing ITC balance for next period.
     * Populated only when {@link com.desitech.vyaparsathi.gst.service.GstOffsetService}
     * is wired.
     */
    private ItcOffsetSummary itcOffset = new ItcOffsetSummary();

    public OutwardSupplies getZeroRatedExportSupplies() { return zeroRatedExportSupplies; }
    public void setZeroRatedExportSupplies(OutwardSupplies v) { this.zeroRatedExportSupplies = v; }

    public OutwardSupplies getNonGstSupplies() { return nonGstSupplies; }
    public void setNonGstSupplies(OutwardSupplies v) { this.nonGstSupplies = v; }

    public List<InterStatePosEntry> getInterStateUnregistered() { return interStateUnregistered; }
    public void setInterStateUnregistered(List<InterStatePosEntry> v) { this.interStateUnregistered = v; }

    public ItcDetails getItcRcm() { return itcRcm; }
    public void setItcRcm(ItcDetails v) { this.itcRcm = v; }

    public ItcDetails getItcOther() { return itcOther; }
    public void setItcOther(ItcDetails v) { this.itcOther = v; }

    public ItcDetails getItcReversed() { return itcReversed; }
    public void setItcReversed(ItcDetails v) { this.itcReversed = v; }

    public ItcDetails getItcNet() { return itcNet; }
    public void setItcNet(ItcDetails v) { this.itcNet = v; }

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

    public ItcOffsetSummary getItcOffset() { return itcOffset; }
    public void setItcOffset(ItcOffsetSummary itcOffset) { this.itcOffset = itcOffset; }

    public static class InterStatePosEntry {
        private String pos;
        private BigDecimal taxableValue = BigDecimal.ZERO;
        private BigDecimal igst = BigDecimal.ZERO;

        public String getPos() { return pos; }
        public void setPos(String pos) { this.pos = pos; }

        public BigDecimal getTaxableValue() { return taxableValue; }
        public void setTaxableValue(BigDecimal v) { this.taxableValue = v; }

        public BigDecimal getIgst() { return igst; }
        public void setIgst(BigDecimal v) { this.igst = v; }
    }

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
        private BigDecimal igstPayable  = BigDecimal.ZERO;
        private BigDecimal cgstPayable  = BigDecimal.ZERO;
        private BigDecimal sgstPayable  = BigDecimal.ZERO;
        private BigDecimal utgstPayable = BigDecimal.ZERO;
        private BigDecimal cessPayable  = BigDecimal.ZERO;

        public BigDecimal getIgstPayable()  { return igstPayable; }
        public void setIgstPayable(BigDecimal v)  { this.igstPayable = v; }

        public BigDecimal getCgstPayable()  { return cgstPayable; }
        public void setCgstPayable(BigDecimal v)  { this.cgstPayable = v; }

        public BigDecimal getSgstPayable()  { return sgstPayable; }
        public void setSgstPayable(BigDecimal v)  { this.sgstPayable = v; }

        public BigDecimal getUtgstPayable() { return utgstPayable; }
        public void setUtgstPayable(BigDecimal v) { this.utgstPayable = v; }

        public BigDecimal getCessPayable()  { return cessPayable; }
        public void setCessPayable(BigDecimal v)  { this.cessPayable = v; }
    }

    /**
     * Section 6 — ITC utilisation detail (Rule 88A/88B offset).
     *
     * <ul>
     *   <li>{@code paidThroughItc} — how much of each head's liability was offset
     *       through ITC (cross-head offsets reflected in the head that received the
     *       payment, not the ITC head that provided it).</li>
     *   <li>{@code paidInCash} — remaining liability to be discharged via PMT-06
     *       Electronic Cash Ledger.</li>
     *   <li>{@code closingItcBalance} — unused ITC carried forward to next period.</li>
     * </ul>
     */
    @Data
    public static class ItcOffsetSummary {
        private ItcDetails paidThroughItc    = new ItcDetails();
        private ItcDetails paidInCash        = new ItcDetails();
        private ItcDetails closingItcBalance = new ItcDetails();

        public ItcDetails getPaidThroughItc()    { return paidThroughItc; }
        public void setPaidThroughItc(ItcDetails v)    { this.paidThroughItc = v; }

        public ItcDetails getPaidInCash()        { return paidInCash; }
        public void setPaidInCash(ItcDetails v)        { this.paidInCash = v; }

        public ItcDetails getClosingItcBalance() { return closingItcBalance; }
        public void setClosingItcBalance(ItcDetails v) { this.closingItcBalance = v; }
    }
}
