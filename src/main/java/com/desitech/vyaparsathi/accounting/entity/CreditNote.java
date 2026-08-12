package com.desitech.vyaparsathi.accounting.entity;

import com.desitech.vyaparsathi.accounting.enums.CreditNoteStatus;
import com.desitech.vyaparsathi.common.entities.ShopAwareEntity;
import com.desitech.vyaparsathi.customer.entity.Customer;
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
public class CreditNote extends ShopAwareEntity {

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
}
