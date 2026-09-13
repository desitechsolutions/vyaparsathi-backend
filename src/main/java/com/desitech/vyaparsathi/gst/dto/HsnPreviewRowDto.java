package com.desitech.vyaparsathi.gst.dto;

import java.math.BigDecimal;

/** One row in the HSN pre-download preview widget. */
public class HsnPreviewRowDto {

    private int        num;
    private String     hsnSc;
    private String     desc;
    private String     uqc;
    private double     rt;
    private BigDecimal qty;
    private BigDecimal val;
    private BigDecimal txval;
    private BigDecimal iamt;
    private BigDecimal camt;
    private BigDecimal samt;
    private BigDecimal csamt;

    public HsnPreviewRowDto() {}

    public HsnPreviewRowDto(int num, String hsnSc, String desc, String uqc, double rt,
                             BigDecimal qty, BigDecimal val, BigDecimal txval,
                             BigDecimal iamt, BigDecimal camt, BigDecimal samt, BigDecimal csamt) {
        this.num   = num;
        this.hsnSc = hsnSc;
        this.desc  = desc;
        this.uqc   = uqc;
        this.rt    = rt;
        this.qty   = qty;
        this.val   = val;
        this.txval = txval;
        this.iamt  = iamt;
        this.camt  = camt;
        this.samt  = samt;
        this.csamt = csamt;
    }

    public int getNum() { return num; }
    public void setNum(int num) { this.num = num; }
    public String getHsnSc() { return hsnSc; }
    public void setHsnSc(String hsnSc) { this.hsnSc = hsnSc; }
    public String getDesc() { return desc; }
    public void setDesc(String desc) { this.desc = desc; }
    public String getUqc() { return uqc; }
    public void setUqc(String uqc) { this.uqc = uqc; }
    public double getRt() { return rt; }
    public void setRt(double rt) { this.rt = rt; }
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
    public BigDecimal getCsamt() { return csamt; }
    public void setCsamt(BigDecimal csamt) { this.csamt = csamt; }
}
