package com.desitech.vyaparsathi.gst.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * GSTR-1 filing payload conforming to GSTN V3.2 JSON schema.
 *
 * <p>All field names carry {@code @JsonProperty} annotations that emit the
 * snake_case identifiers required by the GSTN portal. The class hierarchy
 * mirrors the portal schema exactly:
 * <ul>
 *   <li>{@code b2b}   — Table 4: B2B invoices grouped by counterparty GSTIN ({@code ctin})</li>
 *   <li>{@code b2cl}  — Table 5: B2C large invoices (&gt;₹2.5L inter-state) grouped by POS</li>
 *   <li>{@code b2cs}  — Table 7: B2C small summary, one aggregated row per (POS × rate)</li>
 *   <li>{@code cdnr}  — Table 9: Credit/Debit Notes to registered recipients, grouped by {@code ctin}</li>
 *   <li>{@code cdnur} — Table 10: Credit/Debit Notes to unregistered recipients</li>
 *   <li>{@code exp}   — Table 6A: Export invoices grouped by export type (WPAY / WOPAY)</li>
 *   <li>{@code hsn}   — Table 12: HSN summary ({@code { "data": [...] }} wrapper)</li>
 * </ul>
 */
public class Gstr1ExportDto {

    @JsonProperty("gstin")
    private String gstin;

    /** Filing period — format {@code MMYYYY} (e.g. {@code "042025"} for April 2025). */
    @JsonProperty("fp")
    private String fp;

    /** Gross aggregate turnover for the period. GSTN field {@code "gt"}. */
    @JsonProperty("gt")
    private BigDecimal grossTurnover = BigDecimal.ZERO;

    @JsonProperty("b2b")
    private List<B2bPartyGroup> b2b = new ArrayList<>();

    @JsonProperty("b2cl")
    private List<B2clPosGroup> b2cl = new ArrayList<>();

    @JsonProperty("b2cs")
    private List<B2cSummary> b2cs = new ArrayList<>();

    @JsonProperty("cdnr")
    private List<CdnrPartyGroup> cdnr = new ArrayList<>();

    @JsonProperty("cdnur")
    private List<CdnurEntry> cdnur = new ArrayList<>();

    @JsonProperty("exp")
    private List<ExpTypeGroup> exp = new ArrayList<>();

    @JsonProperty("hsn")
    private HsnSection hsn = new HsnSection();

    public String getGstin() { return gstin; }
    public void setGstin(String gstin) { this.gstin = gstin; }
    public String getFp() { return fp; }
    public void setFp(String fp) { this.fp = fp; }
    public BigDecimal getGrossTurnover() { return grossTurnover; }
    public void setGrossTurnover(BigDecimal grossTurnover) { this.grossTurnover = grossTurnover; }
    public List<B2bPartyGroup> getB2b() { return b2b; }
    public void setB2b(List<B2bPartyGroup> b2b) { this.b2b = b2b; }
    public List<B2clPosGroup> getB2cl() { return b2cl; }
    public void setB2cl(List<B2clPosGroup> b2cl) { this.b2cl = b2cl; }
    public List<B2cSummary> getB2cs() { return b2cs; }
    public void setB2cs(List<B2cSummary> b2cs) { this.b2cs = b2cs; }
    public List<CdnrPartyGroup> getCdnr() { return cdnr; }
    public void setCdnr(List<CdnrPartyGroup> cdnr) { this.cdnr = cdnr; }
    public List<CdnurEntry> getCdnur() { return cdnur; }
    public void setCdnur(List<CdnurEntry> cdnur) { this.cdnur = cdnur; }
    public List<ExpTypeGroup> getExp() { return exp; }
    public void setExp(List<ExpTypeGroup> exp) { this.exp = exp; }
    public HsnSection getHsn() { return hsn; }
    public void setHsn(HsnSection hsn) { this.hsn = hsn; }

    // ── B2B — Table 4 ──────────────────────────────────────────────────────────

    /** All invoices to one registered counterparty, keyed by {@code ctin}. */
    public static class B2bPartyGroup {
        @JsonProperty("ctin") private String ctin;
        @JsonProperty("cpty") private String counterpartyName;
        @JsonProperty("inv")  private List<B2bInv> inv = new ArrayList<>();

        public String getCtin() { return ctin; }
        public void setCtin(String ctin) { this.ctin = ctin; }
        public String getCounterpartyName() { return counterpartyName; }
        public void setCounterpartyName(String n) { this.counterpartyName = n; }
        public List<B2bInv> getInv() { return inv; }
        public void setInv(List<B2bInv> inv) { this.inv = inv; }
    }

    /** One B2B invoice within a {@link B2bPartyGroup}. */
    public static class B2bInv {
        @JsonProperty("inum")    private String inum;
        @JsonProperty("idt")     private String idt;
        @JsonProperty("val")     private BigDecimal val;
        @JsonProperty("pos")     private String pos;
        @JsonProperty("rchrg")   private String rchrg   = "N";
        @JsonProperty("inv_typ") private String invTyp   = "R";
        @JsonProperty("itms")    private List<TaxItemEntry> itms = new ArrayList<>();

        public String getInum() { return inum; }
        public void setInum(String v) { this.inum = v; }
        public String getIdt() { return idt; }
        public void setIdt(String v) { this.idt = v; }
        public BigDecimal getVal() { return val; }
        public void setVal(BigDecimal v) { this.val = v; }
        public String getPos() { return pos; }
        public void setPos(String v) { this.pos = v; }
        public String getRchrg() { return rchrg; }
        public void setRchrg(String v) { this.rchrg = v; }
        public String getInvTyp() { return invTyp; }
        public void setInvTyp(String v) { this.invTyp = v; }
        public List<TaxItemEntry> getItms() { return itms; }
        public void setItms(List<TaxItemEntry> v) { this.itms = v; }
    }

    // ── B2CL — Table 5 ─────────────────────────────────────────────────────────

    /** All large B2C invoices to one Place of Supply. */
    public static class B2clPosGroup {
        @JsonProperty("pos") private String pos;
        @JsonProperty("inv") private List<B2clInv> inv = new ArrayList<>();

        public String getPos() { return pos; }
        public void setPos(String v) { this.pos = v; }
        public List<B2clInv> getInv() { return inv; }
        public void setInv(List<B2clInv> v) { this.inv = v; }
    }

    /** One B2CL invoice within a {@link B2clPosGroup}. */
    public static class B2clInv {
        @JsonProperty("inum")   private String inum;
        @JsonProperty("idt")    private String idt;
        @JsonProperty("val")    private BigDecimal val;
        @JsonProperty("sbpcode") private String sbpcode;
        @JsonProperty("sbnum")  private String sbnum;
        @JsonProperty("sbdt")   private String sbdt;
        @JsonProperty("itms")   private List<TaxItemEntry> itms = new ArrayList<>();

        public String getInum() { return inum; }
        public void setInum(String v) { this.inum = v; }
        public String getIdt() { return idt; }
        public void setIdt(String v) { this.idt = v; }
        public BigDecimal getVal() { return val; }
        public void setVal(BigDecimal v) { this.val = v; }
        public String getSbpcode() { return sbpcode; }
        public void setSbpcode(String v) { this.sbpcode = v; }
        public String getSbnum() { return sbnum; }
        public void setSbnum(String v) { this.sbnum = v; }
        public String getSbdt() { return sbdt; }
        public void setSbdt(String v) { this.sbdt = v; }
        public List<TaxItemEntry> getItms() { return itms; }
        public void setItms(List<TaxItemEntry> v) { this.itms = v; }
    }

    // ── B2CS — Table 7 ─────────────────────────────────────────────────────────

    /**
     * One B2CS period summary row, aggregated over the entire filing period.
     * GSTN requires one row per (Place of Supply × GST rate) combination.
     */
    public static class B2cSummary {
        /** {@code INTRA} or {@code INTER}. */
        @JsonProperty("sply_ty") private String splyTy = "INTRA";
        @JsonProperty("pos")     private String pos;
        @JsonProperty("rt")      private double rt;
        @JsonProperty("txval")   private BigDecimal txval  = BigDecimal.ZERO;
        /** {@code OE} = ordinary/exports; {@code E} = LUT/Bond exports. Always {@code OE} for domestic. */
        @JsonProperty("typ")     private String typ    = "OE";
        @JsonProperty("iamt")    private BigDecimal iamt   = BigDecimal.ZERO;
        @JsonProperty("camt")    private BigDecimal camt   = BigDecimal.ZERO;
        @JsonProperty("samt")    private BigDecimal samt   = BigDecimal.ZERO;
        @JsonProperty("csamt")   private BigDecimal csamt  = BigDecimal.ZERO;

        public String getSplyTy() { return splyTy; }
        public void setSplyTy(String v) { this.splyTy = v; }
        public String getPos() { return pos; }
        public void setPos(String v) { this.pos = v; }
        public double getRt() { return rt; }
        public void setRt(double v) { this.rt = v; }
        public BigDecimal getTxval() { return txval; }
        public void setTxval(BigDecimal v) { this.txval = v; }
        public String getTyp() { return typ; }
        public void setTyp(String v) { this.typ = v; }
        public BigDecimal getIamt() { return iamt; }
        public void setIamt(BigDecimal v) { this.iamt = v; }
        public BigDecimal getCamt() { return camt; }
        public void setCamt(BigDecimal v) { this.camt = v; }
        public BigDecimal getSamt() { return samt; }
        public void setSamt(BigDecimal v) { this.samt = v; }
        public BigDecimal getCsamt() { return csamt; }
        public void setCsamt(BigDecimal v) { this.csamt = v; }
    }

    // ── CDNR — Table 9 ─────────────────────────────────────────────────────────

    /** All credit/debit notes to one registered counterparty. */
    public static class CdnrPartyGroup {
        @JsonProperty("ctin") private String ctin;
        @JsonProperty("nt")   private List<CdnrNoteEntry> nt = new ArrayList<>();

        public String getCtin() { return ctin; }
        public void setCtin(String v) { this.ctin = v; }
        public List<CdnrNoteEntry> getNt() { return nt; }
        public void setNt(List<CdnrNoteEntry> v) { this.nt = v; }
    }

    /** One CDNR note within a {@link CdnrPartyGroup}. */
    public static class CdnrNoteEntry {
        @JsonProperty("ntty")    private String ntty   = "C";
        @JsonProperty("nt_num")  private String ntNum;
        @JsonProperty("nt_dt")   private String ntDt;
        @JsonProperty("val")     private BigDecimal val;
        @JsonProperty("pos")     private String pos;
        @JsonProperty("rchrg")   private String rchrg  = "N";
        @JsonProperty("inv_typ") private String invTyp  = "R";
        @JsonProperty("inum")    private String inum;
        @JsonProperty("idt")     private String idt;
        @JsonProperty("itms")    private List<TaxItemEntry> itms = new ArrayList<>();

        public String getNtty() { return ntty; }
        public void setNtty(String v) { this.ntty = v; }
        public String getNtNum() { return ntNum; }
        public void setNtNum(String v) { this.ntNum = v; }
        public String getNtDt() { return ntDt; }
        public void setNtDt(String v) { this.ntDt = v; }
        public BigDecimal getVal() { return val; }
        public void setVal(BigDecimal v) { this.val = v; }
        public String getPos() { return pos; }
        public void setPos(String v) { this.pos = v; }
        public String getRchrg() { return rchrg; }
        public void setRchrg(String v) { this.rchrg = v; }
        public String getInvTyp() { return invTyp; }
        public void setInvTyp(String v) { this.invTyp = v; }
        public String getInum() { return inum; }
        public void setInum(String v) { this.inum = v; }
        public String getIdt() { return idt; }
        public void setIdt(String v) { this.idt = v; }
        public List<TaxItemEntry> getItms() { return itms; }
        public void setItms(List<TaxItemEntry> v) { this.itms = v; }
    }

    // ── CDNUR — Table 10 ───────────────────────────────────────────────────────

    /** One unregistered credit/debit note. */
    public static class CdnurEntry {
        /** {@code B2CL}, {@code EXPWP}, or {@code EXPWOP}. */
        @JsonProperty("typ")    private String typ;
        @JsonProperty("ntty")   private String ntty  = "C";
        @JsonProperty("nt_num") private String ntNum;
        @JsonProperty("nt_dt")  private String ntDt;
        @JsonProperty("val")    private BigDecimal val;
        @JsonProperty("pos")    private String pos;
        @JsonProperty("itms")   private List<TaxItemEntry> itms = new ArrayList<>();

        public String getTyp() { return typ; }
        public void setTyp(String v) { this.typ = v; }
        public String getNtty() { return ntty; }
        public void setNtty(String v) { this.ntty = v; }
        public String getNtNum() { return ntNum; }
        public void setNtNum(String v) { this.ntNum = v; }
        public String getNtDt() { return ntDt; }
        public void setNtDt(String v) { this.ntDt = v; }
        public BigDecimal getVal() { return val; }
        public void setVal(BigDecimal v) { this.val = v; }
        public String getPos() { return pos; }
        public void setPos(String v) { this.pos = v; }
        public List<TaxItemEntry> getItms() { return itms; }
        public void setItms(List<TaxItemEntry> v) { this.itms = v; }
    }

    // ── EXP — Table 6A ─────────────────────────────────────────────────────────

    /** All export invoices under one export type ({@code WPAY} / {@code WOPAY}). */
    public static class ExpTypeGroup {
        @JsonProperty("exp_typ") private String expTyp;
        @JsonProperty("inv")     private List<ExpInv> inv = new ArrayList<>();

        public String getExpTyp() { return expTyp; }
        public void setExpTyp(String v) { this.expTyp = v; }
        public List<ExpInv> getInv() { return inv; }
        public void setInv(List<ExpInv> v) { this.inv = v; }
    }

    /** One export invoice within an {@link ExpTypeGroup}. */
    public static class ExpInv {
        @JsonProperty("inum")    private String inum;
        @JsonProperty("idt")     private String idt;
        @JsonProperty("val")     private BigDecimal val;
        @JsonProperty("sbpcode") private String sbpcode;
        @JsonProperty("sbnum")   private String sbnum;
        @JsonProperty("sbdt")    private String sbdt;
        @JsonProperty("itms")    private List<TaxItemEntry> itms = new ArrayList<>();

        public String getInum() { return inum; }
        public void setInum(String v) { this.inum = v; }
        public String getIdt() { return idt; }
        public void setIdt(String v) { this.idt = v; }
        public BigDecimal getVal() { return val; }
        public void setVal(BigDecimal v) { this.val = v; }
        public String getSbpcode() { return sbpcode; }
        public void setSbpcode(String v) { this.sbpcode = v; }
        public String getSbnum() { return sbnum; }
        public void setSbnum(String v) { this.sbnum = v; }
        public String getSbdt() { return sbdt; }
        public void setSbdt(String v) { this.sbdt = v; }
        public List<TaxItemEntry> getItms() { return itms; }
        public void setItms(List<TaxItemEntry> v) { this.itms = v; }
    }

    // ── TaxItemEntry — itms element with nested itm_det ────────────────────────

    /**
     * One element of the {@code itms} array.
     * GSTN JSON: {@code { "num": 1, "itm_det": { "rt": 18, "txval": 1000, … } }}.
     */
    public static class TaxItemEntry {
        @JsonProperty("num")     private int num;
        @JsonProperty("itm_det") private ItemDetail itmDet = new ItemDetail();

        public int getNum() { return num; }
        public void setNum(int v) { this.num = v; }
        public ItemDetail getItmDet() { return itmDet; }
        public void setItmDet(ItemDetail v) { this.itmDet = v; }
    }

    /** Tax detail for one rate-slab within an invoice/note line. */
    public static class ItemDetail {
        @JsonProperty("rt")    private double rt;
        @JsonProperty("txval") private BigDecimal txval = BigDecimal.ZERO;
        @JsonProperty("iamt")  private BigDecimal iamt  = BigDecimal.ZERO;
        @JsonProperty("camt")  private BigDecimal camt  = BigDecimal.ZERO;
        @JsonProperty("samt")  private BigDecimal samt  = BigDecimal.ZERO;
        @JsonProperty("csamt") private BigDecimal csamt = BigDecimal.ZERO;

        public double getRt() { return rt; }
        public void setRt(double v) { this.rt = v; }
        public BigDecimal getTxval() { return txval; }
        public void setTxval(BigDecimal v) { this.txval = v; }
        public BigDecimal getIamt() { return iamt; }
        public void setIamt(BigDecimal v) { this.iamt = v; }
        public BigDecimal getCamt() { return camt; }
        public void setCamt(BigDecimal v) { this.camt = v; }
        public BigDecimal getSamt() { return samt; }
        public void setSamt(BigDecimal v) { this.samt = v; }
        public BigDecimal getCsamt() { return csamt; }
        public void setCsamt(BigDecimal v) { this.csamt = v; }
    }

    // ── HSN — Table 12 ─────────────────────────────────────────────────────────

    /** HSN section wrapper — GSTN JSON: {@code "hsn": { "data": [ … ] } }. */
    public static class HsnSection {
        @JsonProperty("data") private List<HsnEntry> data = new ArrayList<>();

        public List<HsnEntry> getData() { return data; }
        public void setData(List<HsnEntry> v) { this.data = v; }
    }

    /** One HSN summary row within {@link HsnSection}. */
    public static class HsnEntry {
        @JsonProperty("num")    private int num;
        @JsonProperty("hsn_sc") private String hsnSc;
        @JsonProperty("desc")   private String desc;
        @JsonProperty("uqc")    private String uqc;
        @JsonProperty("qty")    private BigDecimal qty   = BigDecimal.ZERO;
        @JsonProperty("val")    private BigDecimal val   = BigDecimal.ZERO;
        @JsonProperty("txval")  private BigDecimal txval = BigDecimal.ZERO;
        @JsonProperty("iamt")   private BigDecimal iamt  = BigDecimal.ZERO;
        @JsonProperty("camt")   private BigDecimal camt  = BigDecimal.ZERO;
        @JsonProperty("samt")   private BigDecimal samt  = BigDecimal.ZERO;
        @JsonProperty("csamt")  private BigDecimal csamt = BigDecimal.ZERO;

        public int getNum() { return num; }
        public void setNum(int v) { this.num = v; }
        public String getHsnSc() { return hsnSc; }
        public void setHsnSc(String v) { this.hsnSc = v; }
        public String getDesc() { return desc; }
        public void setDesc(String v) { this.desc = v; }
        public String getUqc() { return uqc; }
        public void setUqc(String v) { this.uqc = v; }
        public BigDecimal getQty() { return qty; }
        public void setQty(BigDecimal v) { this.qty = v; }
        public BigDecimal getVal() { return val; }
        public void setVal(BigDecimal v) { this.val = v; }
        public BigDecimal getTxval() { return txval; }
        public void setTxval(BigDecimal v) { this.txval = v; }
        public BigDecimal getIamt() { return iamt; }
        public void setIamt(BigDecimal v) { this.iamt = v; }
        public BigDecimal getCamt() { return camt; }
        public void setCamt(BigDecimal v) { this.camt = v; }
        public BigDecimal getSamt() { return samt; }
        public void setSamt(BigDecimal v) { this.samt = v; }
        public BigDecimal getCsamt() { return csamt; }
        public void setCsamt(BigDecimal v) { this.csamt = v; }
    }
}
