package com.desitech.vyaparsathi.accounting.entity;

import com.desitech.vyaparsathi.accounting.enums.CreditNoteStatus;
import com.desitech.vyaparsathi.common.entities.AuditableFinancialEntity;
import com.desitech.vyaparsathi.customer.entity.Customer;
import com.desitech.vyaparsathi.gst.enums.NoteType;
import com.desitech.vyaparsathi.sales.entity.Sale;
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
@Table(name = "credit_notes")
@Getter
@Setter
@NoArgsConstructor
public class CreditNote extends AuditableFinancialEntity {

    @Column(name = "credit_note_no", nullable = false, length = 50)
    private String creditNoteNo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sale_id")
    private Sale sale;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @Column(name = "credit_note_date", nullable = false)
    private LocalDate creditNoteDate;

    @Column(name = "reason", nullable = false)
    private String reason;

    @Column(name = "taxable_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal taxableAmount = BigDecimal.ZERO;

    @Column(name = "cgst_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal cgstAmount = BigDecimal.ZERO;

    @Column(name = "sgst_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal sgstAmount = BigDecimal.ZERO;

    @Column(name = "igst_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal igstAmount = BigDecimal.ZERO;

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount = BigDecimal.ZERO;

    /** How much of the credit has been applied against invoices / refunds so far. */
    @Column(name = "applied_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal appliedAmount = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private CreditNoteStatus status = CreditNoteStatus.ISSUED;

    @Column(name = "notes", length = 500)
    private String notes;

    // ─── V132 GST Phase 1 fields ──────────────────────────────────────────────

    /**
     * Free-text snapshot of the original invoice number at creation time.
     * Written to GSTR-1 CDNR / CDNUR {@code "inum"} field.
     * <p>
     * This field is the <em>authoritative</em> reference for GST filing — it must
     * survive even if the linked {@link Sale} row is deleted or {@code sale_id}
     * is nullified by a cascade. Populated by {@code CreditNoteService} from
     * {@code sale.getInvoiceNo()} at creation; <strong>never update after that</strong>.
     */
    @Column(name = "reference_invoice_number", length = 50)
    private String referenceInvoiceNumber;

    /**
     * GSTR-1 table routing for this credit note.
     * <ul>
     *   <li>{@link NoteType#CDNR} — customer has a GSTIN; goes to GSTR-1 Table 9.</li>
     *   <li>{@link NoteType#CDNUR} — unregistered B2C customer; goes to GSTR-1 Table 10.</li>
     * </ul>
     * Set at creation from the customer's GSTIN via
     * {@link NoteType#fromCustomerGstin(String)}. Immutable once set.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "note_type", nullable = false, length = 10)
    private NoteType noteType = NoteType.CDNR;

    /**
     * Aggregated compensation cess on this credit note.
     * Populated from the sum of {@link CreditNoteItem#getCessAmt()}.
     * Reported in GSTR-1 CDNR/CDNUR {@code "csamt"} field.
     */
    @Column(name = "cess_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal cessAmount = BigDecimal.ZERO;

    // ─── V101 enterprise fields ─────────────────────────────────────
    /** Enum-tagged reason code (§34 CGST). Paired with the free-text
     *  {@code reason} column — the string stays authoritative for display
     *  and legal record; the code drives compliance grouping / GSTR-1. */
    @Enumerated(EnumType.STRING)
    @Column(name = "reason_code", length = 30)
    private com.desitech.vyaparsathi.accounting.enums.CreditNoteReasonCode reasonCode;

    /** True when the associated goods were flowed back into stock on
     *  approval. Purely informational — the stock movement itself is
     *  fired by CreditNoteService. */
    @Column(name = "restock_items", nullable = false)
    private Boolean restockItems = Boolean.FALSE;

    /** True when the credit was settled via cash / bank refund and cannot
     *  be applied to further invoices. Set by CreditNoteAllocationService
     *  on the REFUND path. */
    @Column(name = "refunded", nullable = false)
    private Boolean refunded = Boolean.FALSE;

    @OneToMany(mappedBy = "creditNote", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JsonManagedReference
    private List<CreditNoteItem> items = new ArrayList<>();

    public String getCreditNoteNo() { return creditNoteNo; }
    public void setCreditNoteNo(String creditNoteNo) { this.creditNoteNo = creditNoteNo; }

    public Sale getSale() { return sale; }
    public void setSale(Sale sale) { this.sale = sale; }

    public Customer getCustomer() { return customer; }
    public void setCustomer(Customer customer) { this.customer = customer; }

    public LocalDate getCreditNoteDate() { return creditNoteDate; }
    public void setCreditNoteDate(LocalDate creditNoteDate) { this.creditNoteDate = creditNoteDate; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public BigDecimal getTaxableAmount() { return taxableAmount; }
    public void setTaxableAmount(BigDecimal taxableAmount) { this.taxableAmount = taxableAmount; }

    public BigDecimal getCgstAmount() { return cgstAmount; }
    public void setCgstAmount(BigDecimal cgstAmount) { this.cgstAmount = cgstAmount; }

    public BigDecimal getSgstAmount() { return sgstAmount; }
    public void setSgstAmount(BigDecimal sgstAmount) { this.sgstAmount = sgstAmount; }

    public BigDecimal getIgstAmount() { return igstAmount; }
    public void setIgstAmount(BigDecimal igstAmount) { this.igstAmount = igstAmount; }

    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }

    public BigDecimal getAppliedAmount() { return appliedAmount; }
    public void setAppliedAmount(BigDecimal appliedAmount) { this.appliedAmount = appliedAmount; }

    public com.desitech.vyaparsathi.accounting.enums.CreditNoteReasonCode getReasonCode() { return reasonCode; }
    public void setReasonCode(com.desitech.vyaparsathi.accounting.enums.CreditNoteReasonCode reasonCode) { this.reasonCode = reasonCode; }

    public Boolean getRestockItems() { return restockItems; }
    public void setRestockItems(Boolean restockItems) { this.restockItems = restockItems != null && restockItems; }

    public Boolean getRefunded() { return refunded; }
    public void setRefunded(Boolean refunded) { this.refunded = refunded != null && refunded; }

    /** Remaining credit — the "unused" balance rendered in the UI. */
    public BigDecimal getOutstandingAmount() {
        BigDecimal total = totalAmount == null ? BigDecimal.ZERO : totalAmount;
        BigDecimal applied = appliedAmount == null ? BigDecimal.ZERO : appliedAmount;
        BigDecimal rem = total.subtract(applied);
        return rem.signum() < 0 ? BigDecimal.ZERO : rem;
    }

    public CreditNoteStatus getStatus() { return status; }
    public void setStatus(CreditNoteStatus status) { this.status = status; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public List<CreditNoteItem> getItems() { return items; }
    public void setItems(List<CreditNoteItem> items) { this.items = items; }

    public void addItem(CreditNoteItem item) {
        item.setCreditNote(this);
        this.items.add(item);
    }

    public String getReferenceInvoiceNumber() { return referenceInvoiceNumber; }
    /** Set once at creation from sale.getInvoiceNo(). Do not call after first persist. */
    public void setReferenceInvoiceNumber(String referenceInvoiceNumber) {
        this.referenceInvoiceNumber = referenceInvoiceNumber;
    }

    public NoteType getNoteType() { return noteType; }
    public void setNoteType(NoteType noteType) {
        this.noteType = noteType != null ? noteType : NoteType.CDNR;
    }

    public BigDecimal getCessAmount() { return cessAmount; }
    public void setCessAmount(BigDecimal cessAmount) {
        this.cessAmount = cessAmount != null ? cessAmount : BigDecimal.ZERO;
    }

    /** Recomputes cessAmount from all line items. Call after modifying items collection. */
    public void recomputeCessAmount() {
        this.cessAmount = items.stream()
                .map(item -> item.getCessAmt() != null ? item.getCessAmt() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
