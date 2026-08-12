package com.desitech.vyaparsathi.sales.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class SaleDueDto {
    private Long saleId;
    private String invoiceNo;
    private BigDecimal dueAmount;
    private Long customerId;
    private LocalDateTime date;
    private BigDecimal totalAmount;
    private BigDecimal paidAmount;
    private String customerName;
    private String addressLine1;
    private String city;
    private String state;
    private String GSTIN;
    private String phone;
    private String shopName;
    private String postalCode;
    private String status;
    private String einvoiceStatus;
    private String irn;
    private String ackNo;
    private LocalDateTime ackDate;
    private String qrCodePath;
    private String ewayBillNo;

    /**
     * Sale-level notes (metadata only). Editable post-facto via
     * {@code PATCH /api/sales/{id}/notes}. Never affects ledger or stock.
     */
    private String notes;

    /**
     * INVOICE / PROFORMA / BILL_OF_SUPPLY — surfaced so the FE can offer
     * type-specific actions (e.g. "Convert to Invoice" only appears on PROFORMA
     * rows). Serialized as the enum name.
     */
    private String saleType;

    /**
     * Whether the server would accept a {@code POST /api/sales/{id}/cancel} for this sale.
     * True iff status is COMPLETED or PARTIALLY_RETURNED (i.e. mirrors the guards in
     * {@code SaleService.cancelSale}). Frontends may layer additional UX gates on top
     * (e.g. "today only"), but should never offer Cancel when this is false — the API
     * will reject it.
     */
    private boolean canCancel;

    /**
     * Whether the server would accept a {@code POST /api/sales/{id}/return} for this sale.
     * Same lifecycle rule as {@link #canCancel} — RETURNED / CANCELLED / DRAFT rows return false.
     */
    private boolean canReturn;
}
