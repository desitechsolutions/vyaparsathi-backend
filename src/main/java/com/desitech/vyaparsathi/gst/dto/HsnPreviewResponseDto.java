package com.desitech.vyaparsathi.gst.dto;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** HSN Table 12 preview — rows plus period totals for the KPI strip. */
public class HsnPreviewResponseDto {

    private List<HsnPreviewRowDto> rows         = new ArrayList<>();
    private BigDecimal             totalTaxable = BigDecimal.ZERO;
    private BigDecimal             cgst         = BigDecimal.ZERO;
    private BigDecimal             sgst         = BigDecimal.ZERO;
    private BigDecimal             igst         = BigDecimal.ZERO;
    private BigDecimal             cess         = BigDecimal.ZERO;

    public List<HsnPreviewRowDto> getRows() { return rows; }
    public void setRows(List<HsnPreviewRowDto> rows) { this.rows = rows; }
    public BigDecimal getTotalTaxable() { return totalTaxable; }
    public void setTotalTaxable(BigDecimal totalTaxable) { this.totalTaxable = totalTaxable; }
    public BigDecimal getCgst() { return cgst; }
    public void setCgst(BigDecimal cgst) { this.cgst = cgst; }
    public BigDecimal getSgst() { return sgst; }
    public void setSgst(BigDecimal sgst) { this.sgst = sgst; }
    public BigDecimal getIgst() { return igst; }
    public void setIgst(BigDecimal igst) { this.igst = igst; }
    public BigDecimal getCess() { return cess; }
    public void setCess(BigDecimal cess) { this.cess = cess; }
}
