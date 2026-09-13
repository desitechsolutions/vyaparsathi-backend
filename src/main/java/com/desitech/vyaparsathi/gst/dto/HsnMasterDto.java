package com.desitech.vyaparsathi.gst.dto;

import java.math.BigDecimal;

/** API response DTO for HSN master search results. */
public class HsnMasterDto {

    private Long id;
    private String hsnCode;
    private String description;
    private String gstType;
    private BigDecimal defaultRate;
    private String defaultUqc;

    public Long getId()               { return id; }
    public void setId(Long v)           { this.id = v; }

    public String getHsnCode()        { return hsnCode; }
    public void setHsnCode(String v)    { this.hsnCode = v; }

    public String getDescription()    { return description; }
    public void setDescription(String v){ this.description = v; }

    public String getGstType()        { return gstType; }
    public void setGstType(String v)    { this.gstType = v; }

    public BigDecimal getDefaultRate() { return defaultRate; }
    public void setDefaultRate(BigDecimal v) { this.defaultRate = v; }

    public String getDefaultUqc()     { return defaultUqc; }
    public void setDefaultUqc(String v) { this.defaultUqc = v; }
}
