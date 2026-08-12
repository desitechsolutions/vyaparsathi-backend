package com.desitech.vyaparsathi.accounting.entity;

import com.desitech.vyaparsathi.accounting.enums.DebitNoteStatus;
import com.desitech.vyaparsathi.common.entities.ShopAwareEntity;
import com.desitech.vyaparsathi.purchasereturn.entity.PurchaseReturn;
import com.desitech.vyaparsathi.purchases.entity.PurchaseInvoice;
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
@Table(name = "debit_notes")
@Getter
@Setter
@NoArgsConstructor
public class DebitNote extends ShopAwareEntity {

    @Column(name = "debit_note_no", nullable = false, length = 50)
    private String debitNoteNo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "purchase_invoice_id")
    private PurchaseInvoice purchaseInvoice;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_id")
    private Supplier supplier;

    /** Atomic linkage to the {@link PurchaseReturn} that spawned this note (added in V60). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "purchase_return_id")
    private PurchaseReturn purchaseReturn;

    @Column(name = "debit_note_date", nullable = false)
    private LocalDate debitNoteDate;

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

    /** How much of the debit has been applied against supplier payables so far. */
    @Column(name = "applied_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal appliedAmount = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private DebitNoteStatus status = DebitNoteStatus.ISSUED;

    @Column(name = "notes", length = 500)
    private String notes;

    @OneToMany(mappedBy = "debitNote", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JsonManagedReference
    private List<DebitNoteItem> items = new ArrayList<>();

    public String getDebitNoteNo() { return debitNoteNo; }
    public void setDebitNoteNo(String debitNoteNo) { this.debitNoteNo = debitNoteNo; }

    public PurchaseInvoice getPurchaseInvoice() { return purchaseInvoice; }
    public void setPurchaseInvoice(PurchaseInvoice purchaseInvoice) { this.purchaseInvoice = purchaseInvoice; }

    public Supplier getSupplier() { return supplier; }
    public void setSupplier(Supplier supplier) { this.supplier = supplier; }

    public PurchaseReturn getPurchaseReturn() { return purchaseReturn; }
    public void setPurchaseReturn(PurchaseReturn purchaseReturn) { this.purchaseReturn = purchaseReturn; }

    public LocalDate getDebitNoteDate() { return debitNoteDate; }
    public void setDebitNoteDate(LocalDate debitNoteDate) { this.debitNoteDate = debitNoteDate; }

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

    public DebitNoteStatus getStatus() { return status; }
    public void setStatus(DebitNoteStatus status) { this.status = status; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public List<DebitNoteItem> getItems() { return items; }
    public void setItems(List<DebitNoteItem> items) { this.items = items; }

    public void addItem(DebitNoteItem item) {
        item.setDebitNote(this);
        this.items.add(item);
    }
}
