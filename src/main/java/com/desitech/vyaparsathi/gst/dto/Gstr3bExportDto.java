package com.desitech.vyaparsathi.gst.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * GSTN V3.2 portal filing JSON for GSTR-3B.
 * All field names use exact GSTN snake_case keys so Jackson serialises
 * directly to a fileable JSON without any renaming strategy.
 */
public class Gstr3bExportDto {

    @JsonProperty("gstin")       private String gstin;
    @JsonProperty("ret_period")  private String retPeriod;   // MMYYYY
    @JsonProperty("sup_details") private SupDetails supDetails  = new SupDetails();
    @JsonProperty("inter_sup")   private InterSup  interSup    = new InterSup();
    @JsonProperty("itc_elg")     private ItcElg    itcElg      = new ItcElg();
    @JsonProperty("inward_sup")  private InwardSup inwardSup   = new InwardSup();

    public String getGstin()          { return gstin; }
    public void   setGstin(String v)  { this.gstin = v; }

    public String getRetPeriod()         { return retPeriod; }
    public void   setRetPeriod(String v) { this.retPeriod = v; }

    public SupDetails getSupDetails()          { return supDetails; }
    public void       setSupDetails(SupDetails v) { this.supDetails = v; }

    public InterSup getInterSup()          { return interSup; }
    public void     setInterSup(InterSup v) { this.interSup = v; }

    public ItcElg getItcElg()          { return itcElg; }
    public void   setItcElg(ItcElg v)  { this.itcElg = v; }

    public InwardSup getInwardSup()          { return inwardSup; }
    public void      setInwardSup(InwardSup v) { this.inwardSup = v; }

    // ── Section 3 ─────────────────────────────────────────────────────────────

    public static class SupDetails {
        @JsonProperty("osup_det")      private TaxRow outwardTaxable   = new TaxRow();  // 3.1(a)
        @JsonProperty("osup_zero")     private TaxRow zeroRatedExports = new TaxRow();  // 3.1(b)
        @JsonProperty("osup_nil_exmp") private TaxRow nilExempt        = new TaxRow();  // 3.1(c)
        @JsonProperty("isup_rev")      private TaxRow inwardRcm        = new TaxRow();  // 3.1(d)
        @JsonProperty("osup_nongst")   private TaxRow nonGst           = new TaxRow();  // 3.1(e)

        public TaxRow getOutwardTaxable()      { return outwardTaxable; }
        public void   setOutwardTaxable(TaxRow v)   { this.outwardTaxable = v; }

        public TaxRow getZeroRatedExports()    { return zeroRatedExports; }
        public void   setZeroRatedExports(TaxRow v) { this.zeroRatedExports = v; }

        public TaxRow getNilExempt()           { return nilExempt; }
        public void   setNilExempt(TaxRow v)   { this.nilExempt = v; }

        public TaxRow getInwardRcm()           { return inwardRcm; }
        public void   setInwardRcm(TaxRow v)   { this.inwardRcm = v; }

        public TaxRow getNonGst()              { return nonGst; }
        public void   setNonGst(TaxRow v)      { this.nonGst = v; }
    }

    public static class TaxRow {
        @JsonProperty("txval") private BigDecimal txval = BigDecimal.ZERO;
        @JsonProperty("iamt")  private BigDecimal iamt  = BigDecimal.ZERO;
        @JsonProperty("csamt") private BigDecimal csamt = BigDecimal.ZERO;
        @JsonProperty("camt")  private BigDecimal camt  = BigDecimal.ZERO;
        @JsonProperty("samt")  private BigDecimal samt  = BigDecimal.ZERO;

        public BigDecimal getTxval() { return txval; }
        public void       setTxval(BigDecimal v) { this.txval = v; }

        public BigDecimal getIamt()  { return iamt; }
        public void       setIamt(BigDecimal v)  { this.iamt = v; }

        public BigDecimal getCsamt() { return csamt; }
        public void       setCsamt(BigDecimal v) { this.csamt = v; }

        public BigDecimal getCamt()  { return camt; }
        public void       setCamt(BigDecimal v)  { this.camt = v; }

        public BigDecimal getSamt()  { return samt; }
        public void       setSamt(BigDecimal v)  { this.samt = v; }
    }

    // ── Table 3.2 ─────────────────────────────────────────────────────────────

    public static class InterSup {
        @JsonProperty("unreg_details") private List<PosEntry> unregDetails = new ArrayList<>();
        @JsonProperty("comp_details")  private List<PosEntry> compDetails  = new ArrayList<>();
        @JsonProperty("uin_details")   private List<PosEntry> uinDetails   = new ArrayList<>();

        public List<PosEntry> getUnregDetails() { return unregDetails; }
        public void           setUnregDetails(List<PosEntry> v) { this.unregDetails = v; }

        public List<PosEntry> getCompDetails()  { return compDetails; }
        public void           setCompDetails(List<PosEntry> v)  { this.compDetails = v; }

        public List<PosEntry> getUinDetails()   { return uinDetails; }
        public void           setUinDetails(List<PosEntry> v)   { this.uinDetails = v; }
    }

    public static class PosEntry {
        @JsonProperty("pos")   private String     pos;
        @JsonProperty("txval") private BigDecimal txval = BigDecimal.ZERO;
        @JsonProperty("iamt")  private BigDecimal iamt  = BigDecimal.ZERO;

        public String     getPos()  { return pos; }
        public void       setPos(String v)  { this.pos = v; }

        public BigDecimal getTxval() { return txval; }
        public void       setTxval(BigDecimal v) { this.txval = v; }

        public BigDecimal getIamt()  { return iamt; }
        public void       setIamt(BigDecimal v)  { this.iamt = v; }
    }

    // ── Section 4 ─────────────────────────────────────────────────────────────

    public static class ItcElg {
        @JsonProperty("itc_avl")   private List<ItcEntry> itcAvl   = new ArrayList<>();
        @JsonProperty("itc_rev")   private List<ItcEntry> itcRev   = new ArrayList<>();
        @JsonProperty("itc_net")   private ItcAmounts     itcNet   = new ItcAmounts();
        @JsonProperty("itc_inelg") private List<ItcEntry> itcInelg = new ArrayList<>();

        public List<ItcEntry> getItcAvl()  { return itcAvl; }
        public void           setItcAvl(List<ItcEntry> v)  { this.itcAvl = v; }

        public List<ItcEntry> getItcRev()  { return itcRev; }
        public void           setItcRev(List<ItcEntry> v)  { this.itcRev = v; }

        public ItcAmounts     getItcNet()  { return itcNet; }
        public void           setItcNet(ItcAmounts v)      { this.itcNet = v; }

        public List<ItcEntry> getItcInelg() { return itcInelg; }
        public void           setItcInelg(List<ItcEntry> v) { this.itcInelg = v; }
    }

    public static class ItcEntry {
        @JsonProperty("ty")    private String     ty;
        @JsonProperty("iamt")  private BigDecimal iamt  = BigDecimal.ZERO;
        @JsonProperty("csamt") private BigDecimal csamt = BigDecimal.ZERO;
        @JsonProperty("camt")  private BigDecimal camt  = BigDecimal.ZERO;
        @JsonProperty("samt")  private BigDecimal samt  = BigDecimal.ZERO;

        public ItcEntry() {}
        public ItcEntry(String ty, BigDecimal iamt, BigDecimal camt, BigDecimal samt, BigDecimal csamt) {
            this.ty = ty; this.iamt = iamt; this.camt = camt; this.samt = samt; this.csamt = csamt;
        }

        public String     getTy()   { return ty; }
        public void       setTy(String v)   { this.ty = v; }

        public BigDecimal getIamt()  { return iamt; }
        public void       setIamt(BigDecimal v)  { this.iamt = v; }

        public BigDecimal getCsamt() { return csamt; }
        public void       setCsamt(BigDecimal v) { this.csamt = v; }

        public BigDecimal getCamt()  { return camt; }
        public void       setCamt(BigDecimal v)  { this.camt = v; }

        public BigDecimal getSamt()  { return samt; }
        public void       setSamt(BigDecimal v)  { this.samt = v; }
    }

    public static class ItcAmounts {
        @JsonProperty("iamt")  private BigDecimal iamt  = BigDecimal.ZERO;
        @JsonProperty("csamt") private BigDecimal csamt = BigDecimal.ZERO;
        @JsonProperty("camt")  private BigDecimal camt  = BigDecimal.ZERO;
        @JsonProperty("samt")  private BigDecimal samt  = BigDecimal.ZERO;

        public BigDecimal getIamt()  { return iamt; }
        public void       setIamt(BigDecimal v)  { this.iamt = v; }

        public BigDecimal getCsamt() { return csamt; }
        public void       setCsamt(BigDecimal v) { this.csamt = v; }

        public BigDecimal getCamt()  { return camt; }
        public void       setCamt(BigDecimal v)  { this.camt = v; }

        public BigDecimal getSamt()  { return samt; }
        public void       setSamt(BigDecimal v)  { this.samt = v; }
    }

    // ── Section 5 ─────────────────────────────────────────────────────────────

    public static class InwardSup {
        @JsonProperty("isup_details") private List<InwardEntry> isupDetails = new ArrayList<>();

        public List<InwardEntry> getIsupDetails() { return isupDetails; }
        public void              setIsupDetails(List<InwardEntry> v) { this.isupDetails = v; }
    }

    public static class InwardEntry {
        @JsonProperty("ty")    private String     ty;
        @JsonProperty("inter") private BigDecimal inter = BigDecimal.ZERO;
        @JsonProperty("intra") private BigDecimal intra = BigDecimal.ZERO;

        public InwardEntry() {}
        public InwardEntry(String ty, BigDecimal inter, BigDecimal intra) {
            this.ty = ty; this.inter = inter; this.intra = intra;
        }

        public String     getTy()    { return ty; }
        public void       setTy(String v)    { this.ty = v; }

        public BigDecimal getInter() { return inter; }
        public void       setInter(BigDecimal v) { this.inter = v; }

        public BigDecimal getIntra() { return intra; }
        public void       setIntra(BigDecimal v) { this.intra = v; }
    }
}
