package com.desitech.vyaparsathi.receiving.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Summary payload for the 3-way match card on the GRN detail page. Every
 * quantity/amount is expressed on the PO's canonical basis so variance chips
 * can render as absolute deltas (positive = supplier over-invoicing).
 */
public class ThreeWayMatchDto {

    private String matchStatus;
    private BigDecimal poTotal;
    private BigDecimal grnTotal;
    private BigDecimal invoiceTotal;
    private BigDecimal totalVariance;
    private List<LineVariance> lineVariances;

    public static class LineVariance {
        public Long purchaseOrderItemId;
        public String itemName;
        public Integer poQty;
        public Integer grnAcceptedQty;
        public Integer invoiceQty;
        public BigDecimal poUnitCost;
        public BigDecimal grnUnitCost;
        public BigDecimal invoiceUnitCost;
        public BigDecimal costVariancePct;
        public boolean withinTolerance;
    }

    public String getMatchStatus() { return matchStatus; }
    public void setMatchStatus(String matchStatus) { this.matchStatus = matchStatus; }

    public BigDecimal getPoTotal() { return poTotal; }
    public void setPoTotal(BigDecimal poTotal) { this.poTotal = poTotal; }

    public BigDecimal getGrnTotal() { return grnTotal; }
    public void setGrnTotal(BigDecimal grnTotal) { this.grnTotal = grnTotal; }

    public BigDecimal getInvoiceTotal() { return invoiceTotal; }
    public void setInvoiceTotal(BigDecimal invoiceTotal) { this.invoiceTotal = invoiceTotal; }

    public BigDecimal getTotalVariance() { return totalVariance; }
    public void setTotalVariance(BigDecimal totalVariance) { this.totalVariance = totalVariance; }

    public List<LineVariance> getLineVariances() { return lineVariances; }
    public void setLineVariances(List<LineVariance> lineVariances) { this.lineVariances = lineVariances; }
}
