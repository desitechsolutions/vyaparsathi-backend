package com.desitech.vyaparsathi.compliance.service;

import com.desitech.vyaparsathi.accounting.entity.CreditNote;
import com.desitech.vyaparsathi.accounting.entity.CreditNoteItem;
import com.desitech.vyaparsathi.accounting.repository.CreditNoteRepository;
import com.desitech.vyaparsathi.accounting.service.CreditNoteNumberService;
import com.desitech.vyaparsathi.common.util.TenantUtils;
import com.desitech.vyaparsathi.compliance.dto.NoteLineDto;
import com.desitech.vyaparsathi.compliance.dto.StandaloneNoteCreateDto;
import com.desitech.vyaparsathi.customer.entity.Customer;
import com.desitech.vyaparsathi.customer.repository.CustomerRepository;
import com.desitech.vyaparsathi.gst.service.GstJurisdictionService;
import com.desitech.vyaparsathi.gst.util.GstTaxCalculator;
import com.desitech.vyaparsathi.sales.repository.SaleRepository;
import com.desitech.vyaparsathi.shop.entity.Shop;
import com.desitech.vyaparsathi.shop.repository.ShopRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Creates standalone GST compliance credit notes that are not bound to an existing Sale.
 * Used for post-sale adjustments where the originating sale may be outside the system
 * (e.g. inter-branch transfers, court-ordered discounts, manual GSTN corrections).
 */
@Service
public class ComplianceCreditNoteService {

    private final CreditNoteRepository    creditRepo;
    private final CreditNoteNumberService noteNumberService;
    private final CustomerRepository      customerRepo;
    private final SaleRepository          saleRepo;
    private final ShopRepository          shopRepo;
    private final GstJurisdictionService  jurisdictionService;

    public ComplianceCreditNoteService(CreditNoteRepository creditRepo,
                                       CreditNoteNumberService noteNumberService,
                                       CustomerRepository customerRepo,
                                       SaleRepository saleRepo,
                                       ShopRepository shopRepo,
                                       GstJurisdictionService jurisdictionService) {
        this.creditRepo         = creditRepo;
        this.noteNumberService  = noteNumberService;
        this.customerRepo       = customerRepo;
        this.saleRepo           = saleRepo;
        this.shopRepo           = shopRepo;
        this.jurisdictionService = jurisdictionService;
    }

    @Transactional
    public CreditNote create(StandaloneNoteCreateDto dto) {
        Long shopId = TenantUtils.getCurrentShopId();
        Shop shop   = shopRepo.findById(shopId)
                .orElseThrow(() -> new EntityNotFoundException("Shop not found: " + shopId));

        // ── Resolve customer ────────────────────────────────────────────────────
        Customer customer = resolveCustomer(dto);

        // ── Resolve optional linked sale ────────────────────────────────────────
        var linkedSale = dto.getSaleId() != null
                ? saleRepo.findById(dto.getSaleId()).orElse(null)
                : null;

        String refInvNo   = linkedSale != null ? linkedSale.getInvoiceNo()
                : dto.getOriginalInvoiceNumber();
        if (refInvNo == null || refInvNo.isBlank()) {
            throw new IllegalArgumentException(
                    "originalInvoiceNumber is required when saleId is not supplied");
        }

        // ── Determine jurisdiction ──────────────────────────────────────────────
        String shopCode     = jurisdictionService.resolveStateCode(shop).orElse(null);
        String customerCode = customer != null
                              ? jurisdictionService.resolveStateCode(customer).orElse(null)
                              : null;
        boolean intraState  = jurisdictionService.isIntraState(shopCode, customerCode);

        // ── Build note header ───────────────────────────────────────────────────
        CreditNote note = new CreditNote();
        note.setShop(shop);
        note.setCustomer(customer);
        note.setSale(linkedSale);
        note.setNoteType(dto.getNoteType());
        note.setCreditNoteDate(dto.getNoteDate());
        note.setReferenceInvoiceNumber(refInvNo);
        note.setReasonCode(dto.getReasonCode());
        note.setReason(dto.getReasonCode() != null ? dto.getReasonCode().name() : "OTHER");
        note.setNotes(dto.getNotes());
        note.setRestockItems(dto.isRestockItems());

        // ── Build line items ────────────────────────────────────────────────────
        BigDecimal sumTaxable = BigDecimal.ZERO;
        BigDecimal sumCgst    = BigDecimal.ZERO;
        BigDecimal sumSgst    = BigDecimal.ZERO;
        BigDecimal sumIgst    = BigDecimal.ZERO;
        BigDecimal sumCess    = BigDecimal.ZERO;

        for (NoteLineDto lineDto : dto.getItems()) {
            BigDecimal lineDiscount = lineDto.getLineDiscount() != null
                                      ? lineDto.getLineDiscount() : BigDecimal.ZERO;
            BigDecimal cessRate     = lineDto.getCessRate() != null
                                      ? lineDto.getCessRate() : BigDecimal.ZERO;

            BigDecimal taxableValue = lineDto.getQty()
                    .multiply(lineDto.getUnitPrice())
                    .subtract(lineDiscount)
                    .max(BigDecimal.ZERO)
                    .setScale(2, RoundingMode.HALF_UP);

            GstTaxCalculator.LineGst gst = GstTaxCalculator.computeLineGst(
                    taxableValue, lineDto.getGstType(), cessRate, intraState, false);

            CreditNoteItem item = new CreditNoteItem();
            item.setCreditNote(note);
            item.setShop(shop);
            item.setItemName(lineDto.getDescription());
            item.setHsnSac(lineDto.getHsnSac());
            item.setUnit(lineDto.getUnit());
            item.setQty(lineDto.getQty());
            item.setUnitPrice(lineDto.getUnitPrice());
            item.setDiscount(lineDiscount);
            item.setTaxableValue(taxableValue);
            item.setGstType(lineDto.getGstType());
            item.setCgstAmt(gst.cgst());
            item.setSgstAmt(gst.sgst());
            item.setIgstAmt(gst.igst());
            item.setCessRate(cessRate);
            item.setCessAmt(gst.cess());
            item.setTotalAmount(taxableValue.add(gst.totalTax()));

            note.getItems().add(item);

            sumTaxable = sumTaxable.add(taxableValue);
            sumCgst    = sumCgst.add(gst.cgst());
            sumSgst    = sumSgst.add(gst.sgst());
            sumIgst    = sumIgst.add(gst.igst());
            sumCess    = sumCess.add(gst.cess());
        }

        // ── Set header totals ───────────────────────────────────────────────────
        note.setTaxableAmount(sumTaxable);
        note.setCgstAmount(sumCgst);
        note.setSgstAmount(sumSgst);
        note.setIgstAmount(sumIgst);
        note.setCessAmount(sumCess);
        note.setTotalAmount(sumTaxable.add(sumCgst).add(sumSgst).add(sumIgst).add(sumCess));
        note.setCreditNoteNo(noteNumberService.nextCreditNoteNumber(shopId, dto.getNoteDate()));

        return creditRepo.save(note);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────

    private Customer resolveCustomer(StandaloneNoteCreateDto dto) {
        if (dto.getCustomerId() != null) {
            return customerRepo.findById(dto.getCustomerId())
                    .orElseThrow(() -> new EntityNotFoundException(
                            "Customer not found: " + dto.getCustomerId()));
        }
        if (dto.getCustomerGstin() != null && !dto.getCustomerGstin().isBlank()) {
            // ShopFilterAspect auto-scopes findAll() to the current tenant.
            return customerRepo.findAll().stream()
                    .filter(c -> dto.getCustomerGstin().equalsIgnoreCase(c.getGstin()))
                    .findFirst()
                    .orElseThrow(() -> new EntityNotFoundException(
                            "No customer found with GSTIN: " + dto.getCustomerGstin()));
        }
        return null;
    }
}
