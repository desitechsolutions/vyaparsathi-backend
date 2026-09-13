package com.desitech.vyaparsathi.compliance.dto;

import com.desitech.vyaparsathi.accounting.enums.CreditNoteReasonCode;
import com.desitech.vyaparsathi.gst.enums.NoteType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;

/**
 * Request body for {@code POST /api/v1/compliance/credit-notes}.
 *
 * <p>Either {@code saleId} OR ({@code originalInvoiceNumber} + {@code originalInvoiceDate})
 * must be supplied. When {@code saleId} is present the original invoice details are
 * derived from the Sale; when absent they must be provided explicitly.
 */
public class StandaloneNoteCreateDto {

    @NotNull
    private NoteType noteType;

    /** Known customer DB id. When absent, {@code customerGstin} is used for lookup. */
    private Long customerId;

    /** GSTIN of the counterparty — used to resolve the Customer when {@code customerId} absent. */
    private String customerGstin;

    /** Original invoice number being reversed. Required when {@code saleId} is absent. */
    private String originalInvoiceNumber;

    /** Date of the original invoice. Required when {@code saleId} is absent. */
    private LocalDate originalInvoiceDate;

    /** Link to an existing Sale. Optional — use when the credit note reverses a known sale. */
    private Long saleId;

    @NotNull
    private CreditNoteReasonCode reasonCode;

    @NotNull
    private LocalDate noteDate;

    @NotNull @NotEmpty
    private List<@Valid NoteLineDto> items;

    private String  notes;
    private boolean restockItems;

    public NoteType getNoteType() { return noteType; }
    public void setNoteType(NoteType noteType) { this.noteType = noteType; }
    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long customerId) { this.customerId = customerId; }
    public String getCustomerGstin() { return customerGstin; }
    public void setCustomerGstin(String customerGstin) { this.customerGstin = customerGstin; }
    public String getOriginalInvoiceNumber() { return originalInvoiceNumber; }
    public void setOriginalInvoiceNumber(String v) { this.originalInvoiceNumber = v; }
    public LocalDate getOriginalInvoiceDate() { return originalInvoiceDate; }
    public void setOriginalInvoiceDate(LocalDate v) { this.originalInvoiceDate = v; }
    public Long getSaleId() { return saleId; }
    public void setSaleId(Long saleId) { this.saleId = saleId; }
    public CreditNoteReasonCode getReasonCode() { return reasonCode; }
    public void setReasonCode(CreditNoteReasonCode reasonCode) { this.reasonCode = reasonCode; }
    public LocalDate getNoteDate() { return noteDate; }
    public void setNoteDate(LocalDate noteDate) { this.noteDate = noteDate; }
    public List<NoteLineDto> getItems() { return items; }
    public void setItems(List<NoteLineDto> items) { this.items = items; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public boolean isRestockItems() { return restockItems; }
    public void setRestockItems(boolean restockItems) { this.restockItems = restockItems; }
}
