package com.desitech.vyaparsathi.purchases.entity;

import com.desitech.vyaparsathi.common.entities.AuditableFinancialEntity;
import com.desitech.vyaparsathi.receiving.entity.Receiving;
import com.desitech.vyaparsathi.supplier.entity.Supplier;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "purchase_invoices")
@Getter
@Setter
@NoArgsConstructor
public class PurchaseInvoice extends AuditableFinancialEntity {

    @Column(name = "purchase_invoice_no", nullable = false, length = 50)
    private String purchaseInvoiceNo;

    @Column(name = "supplier_invoice_no", length = 50)
    private String supplierInvoiceNo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_id", nullable = false)
    private Supplier supplier;

    /**
     * The GRN (goods receipt) this invoice was created against. When present,
     * {@code PurchaseInvoiceService} skips its own stock-add step — inventory
     * was already incremented when the GRN was recorded. When null, the invoice
     * is a direct-purchase flow (no prior GRN) and still owns the stock-in.
     * Added by V62 to fix the double-stock-count bug.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "receiving_id")
    private Receiving receiving;

    @Column(name = "purchase_date", nullable = false)
    private LocalDate purchaseDate;

    @Column(name = "payment_terms", length = 50)
    private String paymentTerms = "NET_30";

    @Column(name = "total_taxable_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalTaxableAmount = BigDecimal.ZERO;

    @Column(name = "total_cgst", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalCgst = BigDecimal.ZERO;

    @Column(name = "total_sgst", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalSgst = BigDecimal.ZERO;

    @Column(name = "total_igst", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalIgst = BigDecimal.ZERO;

    @Column(name = "total_utgst", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalUtgst = BigDecimal.ZERO;

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Column(name = "paid_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal paidAmount = BigDecimal.ZERO;

    @Column(name = "payment_status", nullable = false, length = 20)
    private String paymentStatus = "PENDING"; // PENDING, PARTIAL, PAID

    @Column(name = "status", nullable = false, length = 20)
    private String status = "COMPLETED";

    @Column(name = "notes", length = 500)
    private String notes;

    /**
     * Tax payable under reverse charge on this inward supply. When TRUE,
     * the shop is liable to pay GST to the government directly and may
     * claim it as ITC (subject to eligibility rules). Feeds into GSTR-3B
     * Section 3.1(d) — Inward supplies liable to reverse charge.
     */
    @Column(name = "reverse_charge", nullable = false)
    private Boolean reverseCharge = false;

    /**
     * Whether the ITC on this purchase invoice can be claimed. Column added by V48
     * (defaults TRUE). Non-eligible cases: blocked credit under §17(5) — motor
     * vehicles for personal use, food & beverages, membership fees, works
     * contract on immovable property, etc. Feeds GSTR-3B Section 4(D).
     */
    @Column(name = "is_itc_eligible", nullable = false)
    private Boolean isItcEligible = true;

    /** ITC eligibility category (INPUTS / CAPITAL_GOODS / SERVICES / INELIGIBLE). Column from V48. */
    @Column(name = "itc_eligibility", length = 30, nullable = false)
    private String itcEligibility = "INPUTS";

    /** GSTR-2B match status populated during reconciliation (V136). */
    @Column(name = "gstr2b_match_status", length = 30)
    private String gstr2bMatchStatus;

    @OneToMany(mappedBy = "purchaseInvoice", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonManagedReference
    private List<PurchaseInvoiceItem> items = new ArrayList<>();

    public String getPurchaseInvoiceNo() { return purchaseInvoiceNo; }
    public void setPurchaseInvoiceNo(String purchaseInvoiceNo) { this.purchaseInvoiceNo = purchaseInvoiceNo; }

    public String getSupplierInvoiceNo() { return supplierInvoiceNo; }
    public void setSupplierInvoiceNo(String supplierInvoiceNo) { this.supplierInvoiceNo = supplierInvoiceNo; }

    public Supplier getSupplier() { return supplier; }
    public void setSupplier(Supplier supplier) { this.supplier = supplier; }

    public Receiving getReceiving() { return receiving; }
    public void setReceiving(Receiving receiving) { this.receiving = receiving; }

    public LocalDate getPurchaseDate() { return purchaseDate; }
    public void setPurchaseDate(LocalDate purchaseDate) { this.purchaseDate = purchaseDate; }
    public LocalDate getInvoiceDate() { return purchaseDate; }

    public String getPaymentTerms() { return paymentTerms; }
    public void setPaymentTerms(String paymentTerms) { this.paymentTerms = paymentTerms; }

    public BigDecimal getTotalTaxableAmount() { return totalTaxableAmount; }
    public void setTotalTaxableAmount(BigDecimal totalTaxableAmount) { this.totalTaxableAmount = totalTaxableAmount; }

    public BigDecimal getTotalCgst() { return totalCgst; }
    public void setTotalCgst(BigDecimal totalCgst) { this.totalCgst = totalCgst; }
    public BigDecimal getCgstAmount() { return totalCgst; }

    public BigDecimal getTotalSgst() { return totalSgst; }
    public void setTotalSgst(BigDecimal totalSgst) { this.totalSgst = totalSgst; }
    public BigDecimal getSgstAmount() { return totalSgst; }

    public BigDecimal getTotalIgst() { return totalIgst; }
    public void setTotalIgst(BigDecimal totalIgst) { this.totalIgst = totalIgst; }
    public BigDecimal getIgstAmount() { return totalIgst; }

    public BigDecimal getTotalUtgst() { return totalUtgst; }
    public void setTotalUtgst(BigDecimal totalUtgst) { this.totalUtgst = totalUtgst; }
    public BigDecimal getUtgstAmount() { return totalUtgst; }

    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }

    public BigDecimal getPaidAmount() { return paidAmount; }
    public void setPaidAmount(BigDecimal paidAmount) { this.paidAmount = paidAmount; }

    public String getPaymentStatus() { return paymentStatus; }
    public void setPaymentStatus(String paymentStatus) { this.paymentStatus = paymentStatus; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public Boolean getReverseCharge() { return reverseCharge; }
    public void setReverseCharge(Boolean reverseCharge) { this.reverseCharge = reverseCharge != null && reverseCharge; }
    public boolean isReverseCharge() { return Boolean.TRUE.equals(reverseCharge); }

    public Boolean getIsItcEligible() { return isItcEligible; }
    public void setIsItcEligible(Boolean isItcEligible) { this.isItcEligible = isItcEligible == null || isItcEligible; }
    public boolean isItcEligible() { return isItcEligible == null || isItcEligible; }

    public String getGstr2bMatchStatus() { return gstr2bMatchStatus; }
    public void setGstr2bMatchStatus(String gstr2bMatchStatus) { this.gstr2bMatchStatus = gstr2bMatchStatus; }

    public String getItcEligibility() { return itcEligibility; }
    public void setItcEligibility(String itcEligibility) { this.itcEligibility = itcEligibility; }

    public List<PurchaseInvoiceItem> getItems() { return items; }
    public void setItems(List<PurchaseInvoiceItem> items) { this.items = items; }
}
