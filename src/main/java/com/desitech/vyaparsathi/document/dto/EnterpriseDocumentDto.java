package com.desitech.vyaparsathi.document.dto;

import com.desitech.vyaparsathi.common.enums.DocumentType;
import com.desitech.vyaparsathi.common.enums.SupplyType;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.awt.Color;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * The universal document payload consumed by {@code EnterpriseDocumentRenderer}.
 * Every printable document — Tax Invoice, Purchase Order, GRN, Delivery
 * Challan, Purchase Return, Debit Note, Credit Note — is mapped into this
 * DTO before rendering. This lets the renderer stay dumb and consistent.
 */
@Getter
@Setter
@NoArgsConstructor
public class EnterpriseDocumentDto {

    // ─── Document identity ──────────────────────────────────────────────
    private DocumentType documentType;
    private Long documentId;
    private String documentNumber;
    private LocalDate documentDate;
    private String fiscalYear;
    private Integer revisionNumber = 0;

    /** DRAFT / CANCELLED / DUPLICATE / VOID / MODIFIED — null means no watermark. */
    private String watermark;

    /** Business status ("APPROVED", "PENDING_APPROVAL" etc.) - shown as pill in header. */
    private String status;

    // ─── Parties ────────────────────────────────────────────────────────
    private PartyDto issuer;
    private PartyDto counterparty;

    /** Optional multi-party routing (collapses to counterparty when null). */
    private PartyDto billTo;
    private PartyDto shipTo;
    private PartyDto consignee;
    private PartyDto boughtFrom;

    // ─── Tax context ────────────────────────────────────────────────────
    private String placeOfSupplyState;
    private String placeOfSupplyStateCode;
    private SupplyType supplyType;
    private Boolean reverseCharge = Boolean.FALSE;

    // ─── Currency ───────────────────────────────────────────────────────
    private String currencyCode = "INR";
    private BigDecimal exchangeRate = BigDecimal.ONE;

    // ─── Reference documents (PO → GRN → INV → DN) ─────────────────────
    private List<DocumentReferenceDto> linkedDocuments = new ArrayList<>();

    // ─── Items + totals + HSN summary ──────────────────────────────────
    private List<LineItemDto> items = new ArrayList<>();
    private TotalsDto totals = new TotalsDto();
    private List<HsnSummaryRowDto> hsnSummary = new ArrayList<>();

    // ─── Optional statutory blocks ─────────────────────────────────────
    private EinvoiceDto eInvoice;
    private EwayBillDto eWayBill;

    // ─── Logistics (optional; renders when present) ────────────────────
    private String transporterName;
    private String transporterGstin;
    private String vehicleNumber;
    private String lrNumber;
    private LocalDate lrDate;
    private LocalDate expectedDeliveryDate;

    // ─── Terms + notes + payment terms ────────────────────────────────
    private List<String> termsAndConditions = new ArrayList<>();
    private String notes;
    private String paymentTerms;
    private LocalDate dueDate;

    // ─── Audit + brand ────────────────────────────────────────────────
    private DocumentAuditDto audit = new DocumentAuditDto();

    /** Hex string like "#2980b9". Renderer falls back to a neutral blue when null. */
    private String brandColorHex;

    /** Base64-decoded logo bytes; null when the shop has not uploaded one. */
    private byte[] logoBytes;

    /** Base64-decoded signature image; null → text-only signatory. */
    private byte[] signatureBytes;

    /** Cached AWT Color derived from brandColorHex — populated by the renderer. */
    private transient Color brandColor;

    /** Extra metadata block (e.g. GRN dock, temp) rendered under the header meta table. */
    private java.util.Map<String, String> extraMetadata = new java.util.LinkedHashMap<>();

    /** Optional locale — reserved for future bilingual rendering. */
    private String locale = "en-IN";
}
