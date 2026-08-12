package com.desitech.vyaparsathi.gst.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
public class Gstr1ExportDto {
    private String gstin;
    private String fp; // Financial Period (e.g. "042025")
    private BigDecimal grossTurnover = BigDecimal.ZERO;

    private List<B2bInvoice> b2b = new ArrayList<>();
    private List<B2cLargeInvoice> b2cl = new ArrayList<>();
    private List<B2cSmallSummary> b2cs = new ArrayList<>();
    private List<CdnrNote> cdnr = new ArrayList<>();
    private List<HsnSummary> hsn = new ArrayList<>();

    public String getGstin() { return gstin; }
    public void setGstin(String gstin) { this.gstin = gstin; }

    public String getFp() { return fp; }
    public void setFp(String fp) { this.fp = fp; }

    public BigDecimal getGrossTurnover() { return grossTurnover; }
    public void setGrossTurnover(BigDecimal grossTurnover) { this.grossTurnover = grossTurnover; }

    public List<B2bInvoice> getB2b() { return b2b; }
    public void setB2b(List<B2bInvoice> b2b) { this.b2b = b2b; }

    public List<B2cLargeInvoice> getB2cl() { return b2cl; }
    public void setB2cl(List<B2cLargeInvoice> b2cl) { this.b2cl = b2cl; }

    public List<B2cSmallSummary> getB2cs() { return b2cs; }
    public void setB2cs(List<B2cSmallSummary> b2cs) { this.b2cs = b2cs; }

    public List<CdnrNote> getCdnr() { return cdnr; }
    public void setCdnr(List<CdnrNote> cdnr) { this.cdnr = cdnr; }

    public List<HsnSummary> getHsn() { return hsn; }
    public void setHsn(List<HsnSummary> hsn) { this.hsn = hsn; }

    @Data
    public static class B2bInvoice {
        private String ctin; // Customer GSTIN
        private String invNo;
        private String invDate;
        private BigDecimal val; // Invoice Value
        private String pos; // Place of Supply (e.g. "07-Delhi")
        private String rchrg = "N"; // Reverse Charge
        private String invType = "R"; // Regular
        private List<TaxItem> items = new ArrayList<>();

        public String getCtin() { return ctin; }
        public void setCtin(String ctin) { this.ctin = ctin; }

        public String getInvNo() { return invNo; }
        public void setInvNo(String invNo) { this.invNo = invNo; }

        public String getInvDate() { return invDate; }
        public void setInvDate(String invDate) { this.invDate = invDate; }

        public BigDecimal getVal() { return val; }
        public void setVal(BigDecimal val) { this.val = val; }

        public String getPos() { return pos; }
        public void setPos(String pos) { this.pos = pos; }

        public String getRchrg() { return rchrg; }
        public void setRchrg(String rchrg) { this.rchrg = rchrg; }

        public String getInvType() { return invType; }
        public void setInvType(String invType) { this.invType = invType; }

        public List<TaxItem> getItems() { return items; }
        public void setItems(List<TaxItem> items) { this.items = items; }
    }

    @Data
    public static class B2cLargeInvoice {
        private String invNo;
        private String invDate;
        private BigDecimal val;
        private String pos;
        private List<TaxItem> items = new ArrayList<>();

        public String getInvNo() { return invNo; }
        public void setInvNo(String invNo) { this.invNo = invNo; }

        public String getInvDate() { return invDate; }
        public void setInvDate(String invDate) { this.invDate = invDate; }

        public BigDecimal getVal() { return val; }
        public void setVal(BigDecimal val) { this.val = val; }

        public String getPos() { return pos; }
        public void setPos(String pos) { this.pos = pos; }

        public List<TaxItem> getItems() { return items; }
        public void setItems(List<TaxItem> items) { this.items = items; }
    }

    @Data
    public static class B2cSmallSummary {
        private String pos;
        private BigDecimal txval;
        private Double rt; // Rate
        private BigDecimal iamti = BigDecimal.ZERO; // IGST
        private BigDecimal camti = BigDecimal.ZERO; // CGST
        private BigDecimal samti = BigDecimal.ZERO; // SGST
        private BigDecimal uamti = BigDecimal.ZERO; // UTGST — for UT-based shops

        public String getPos() { return pos; }
        public void setPos(String pos) { this.pos = pos; }

        public BigDecimal getTxval() { return txval; }
        public void setTxval(BigDecimal txval) { this.txval = txval; }

        public Double getRt() { return rt; }
        public void setRt(Double rt) { this.rt = rt; }

        public BigDecimal getIamti() { return iamti; }
        public void setIamti(BigDecimal iamti) { this.iamti = iamti; }

        public BigDecimal getCamti() { return camti; }
        public void setCamti(BigDecimal camti) { this.camti = camti; }

        public BigDecimal getSamti() { return samti; }
        public void setSamti(BigDecimal samti) { this.samti = samti; }

        public BigDecimal getUamti() { return uamti; }
        public void setUamti(BigDecimal uamti) { this.uamti = uamti; }
    }

    @Data
    public static class CdnrNote {
        private String ctin;
        private String ntNo; // Note Number
        private String ntDt; // Note Date
        private String nty = "C"; // "C" for Credit Note, "D" for Debit Note
        private String inum; // Original Invoice No
        private BigDecimal val;
        private List<TaxItem> items = new ArrayList<>();

        public String getCtin() { return ctin; }
        public void setCtin(String ctin) { this.ctin = ctin; }

        public String getNtNo() { return ntNo; }
        public void setNtNo(String ntNo) { this.ntNo = ntNo; }

        public String getNtDt() { return ntDt; }
        public void setNtDt(String ntDt) { this.ntDt = ntDt; }

        public String getNty() { return nty; }
        public void setNty(String nty) { this.nty = nty; }

        public String getInum() { return inum; }
        public void setInum(String inum) { this.inum = inum; }

        public BigDecimal getVal() { return val; }
        public void setVal(BigDecimal val) { this.val = val; }

        public List<TaxItem> getItems() { return items; }
        public void setItems(List<TaxItem> items) { this.items = items; }
    }

    @Data
    public static class TaxItem {
        /**
         * Line number within the parent invoice/note. GSTN requires this as the
         * "num" field in the JSON schema; kept optional here for backwards compat
         * with earlier callers that don't set it.
         */
        private Integer num;
        private Double rt; // Tax Rate e.g. 18.0
        private BigDecimal txval; // Taxable Value
        private BigDecimal iamt = BigDecimal.ZERO;
        private BigDecimal camt = BigDecimal.ZERO;
        private BigDecimal samt = BigDecimal.ZERO;
        private BigDecimal uamt = BigDecimal.ZERO; // UTGST — for UT-shop supplies
        private BigDecimal csamt = BigDecimal.ZERO; // Cess — kept at zero until cess is modeled

        public Integer getNum() { return num; }
        public void setNum(Integer num) { this.num = num; }

        public Double getRt() { return rt; }
        public void setRt(Double rt) { this.rt = rt; }

        public BigDecimal getTxval() { return txval; }
        public void setTxval(BigDecimal txval) { this.txval = txval; }

        public BigDecimal getIamt() { return iamt; }
        public void setIamt(BigDecimal iamt) { this.iamt = iamt; }

        public BigDecimal getCamt() { return camt; }
        public void setCamt(BigDecimal camt) { this.camt = camt; }

        public BigDecimal getSamt() { return samt; }
        public void setSamt(BigDecimal samt) { this.samt = samt; }

        public BigDecimal getUamt() { return uamt; }
        public void setUamt(BigDecimal uamt) { this.uamt = uamt; }

        public BigDecimal getCsamt() { return csamt; }
        public void setCsamt(BigDecimal csamt) { this.csamt = csamt; }
    }

    @Data
    public static class HsnSummary {
        /** Serial number within the HSN section — GSTN requires uniqueness per row. */
        private Integer num;
        private String hsnSc;
        private String desc;
        /** Unit Quantity Code (GSTN codes: NOS, KGS, PCS, MTR, LTR, BOX, GMS, …). */
        private String uqc;
        private BigDecimal qty = BigDecimal.ZERO;
        private BigDecimal val = BigDecimal.ZERO;
        private BigDecimal txval = BigDecimal.ZERO;
        private BigDecimal iamt = BigDecimal.ZERO;
        private BigDecimal camt = BigDecimal.ZERO;
        private BigDecimal samt = BigDecimal.ZERO;
        private BigDecimal uamt = BigDecimal.ZERO;
        private BigDecimal csamt = BigDecimal.ZERO;

        public Integer getNum() { return num; }
        public void setNum(Integer num) { this.num = num; }

        public String getHsnSc() { return hsnSc; }
        public void setHsnSc(String hsnSc) { this.hsnSc = hsnSc; }

        public String getDesc() { return desc; }
        public void setDesc(String desc) { this.desc = desc; }

        public String getUqc() { return uqc; }
        public void setUqc(String uqc) { this.uqc = uqc; }

        public BigDecimal getQty() { return qty; }
        public void setQty(BigDecimal qty) { this.qty = qty; }

        public BigDecimal getVal() { return val; }
        public void setVal(BigDecimal val) { this.val = val; }

        public BigDecimal getTxval() { return txval; }
        public void setTxval(BigDecimal txval) { this.txval = txval; }

        public BigDecimal getIamt() { return iamt; }
        public void setIamt(BigDecimal iamt) { this.iamt = iamt; }

        public BigDecimal getCamt() { return camt; }
        public void setCamt(BigDecimal camt) { this.camt = camt; }

        public BigDecimal getSamt() { return samt; }
        public void setSamt(BigDecimal samt) { this.samt = samt; }

        public BigDecimal getUamt() { return uamt; }
        public void setUamt(BigDecimal uamt) { this.uamt = uamt; }

        public BigDecimal getCsamt() { return csamt; }
        public void setCsamt(BigDecimal csamt) { this.csamt = csamt; }
    }
}
