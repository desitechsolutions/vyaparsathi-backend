package com.desitech.vyaparsathi.quotation.service;

import com.desitech.vyaparsathi.common.annotations.LogAudit;
import com.desitech.vyaparsathi.common.configs.TenantContext;
import com.desitech.vyaparsathi.common.exception.BusinessValidationException;
import com.desitech.vyaparsathi.common.exception.EntityNotFoundAppException;
import com.desitech.vyaparsathi.customer.dto.CustomerDto;
import com.desitech.vyaparsathi.customer.entity.Customer;
import com.desitech.vyaparsathi.customer.repository.CustomerRepository;
import com.desitech.vyaparsathi.inventory.entity.ItemVariant;
import com.desitech.vyaparsathi.inventory.repository.ItemVariantRepository;
import com.desitech.vyaparsathi.quotation.dto.QuotationDto;
import com.desitech.vyaparsathi.quotation.dto.QuotationItemDto;
import com.desitech.vyaparsathi.quotation.entity.Quotation;
import com.desitech.vyaparsathi.quotation.entity.QuotationItem;
import com.desitech.vyaparsathi.quotation.enums.QuotationStatus;
import com.desitech.vyaparsathi.quotation.repository.QuotationRepository;
import com.desitech.vyaparsathi.sales.dto.SaleDto;
import com.desitech.vyaparsathi.sales.dto.SaleItemDto;
import com.desitech.vyaparsathi.sales.entity.Sale;
import com.desitech.vyaparsathi.sales.enums.GSTType;
import com.desitech.vyaparsathi.sales.repository.SaleRepository;
import com.desitech.vyaparsathi.sales.service.SaleService;
import com.desitech.vyaparsathi.shop.entity.Shop;
import com.desitech.vyaparsathi.shop.repository.ShopRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Core lifecycle service for {@link Quotation}. Handles create/edit within
 * mutable states, state transitions, and conversion to a Sale draft.
 */
@Service
public class QuotationService {

    private static final Logger logger = LoggerFactory.getLogger(QuotationService.class);

    /** Statuses that still allow the user to edit line items and totals. */
    private static final Set<QuotationStatus> EDITABLE = EnumSet.of(QuotationStatus.DRAFT, QuotationStatus.SENT);

    /** Terminal statuses — no further transitions allowed. */
    private static final Set<QuotationStatus> TERMINAL =
            EnumSet.of(QuotationStatus.REJECTED, QuotationStatus.EXPIRED,
                       QuotationStatus.CANCELLED, QuotationStatus.CONVERTED);

    private final QuotationRepository quotationRepo;
    private final QuotationNumberService numberService;
    private final CustomerRepository customerRepo;
    private final ItemVariantRepository variantRepo;
    private final ShopRepository shopRepo;
    private final SaleService saleService;
    private final SaleRepository saleRepo;

    public QuotationService(QuotationRepository quotationRepo,
                            QuotationNumberService numberService,
                            CustomerRepository customerRepo,
                            ItemVariantRepository variantRepo,
                            ShopRepository shopRepo,
                            SaleService saleService,
                            SaleRepository saleRepo) {
        this.quotationRepo = quotationRepo;
        this.numberService = numberService;
        this.customerRepo = customerRepo;
        this.variantRepo = variantRepo;
        this.shopRepo = shopRepo;
        this.saleService = saleService;
        this.saleRepo = saleRepo;
    }

    // ─── CRUD + state transitions ───────────────────────────────────────

    @Transactional
    @LogAudit(action = "CREATE_QUOTATION", entity = "QUOTATION")
    public QuotationDto create(QuotationDto dto) {
        Long shopId = TenantContext.getCurrentShopId();
        Shop shop = shopRepo.findById(shopId)
                .orElseThrow(() -> new EntityNotFoundAppException("Shop", shopId));

        Quotation q = new Quotation();
        q.setShop(shop);
        q.setStatus(QuotationStatus.DRAFT);
        q.setQuotationDate(dto.getQuotationDate() != null ? dto.getQuotationDate() : LocalDateTime.now());
        q.setQuotationNo(numberService.nextQuotationNumber(shopId, q.getQuotationDate().toLocalDate()));

        applyFieldsFromDto(q, dto);
        rebuildItems(q, dto);
        recomputeTotals(q);

        Quotation saved = quotationRepo.save(q);
        logger.info("Created quotation {} for shopId={}", saved.getQuotationNo(), shopId);
        return toDto(saved);
    }

    @Transactional
    @LogAudit(action = "UPDATE_QUOTATION", entity = "QUOTATION")
    public QuotationDto update(Long id, QuotationDto dto) {
        Quotation q = quotationRepo.findById(id)
                .orElseThrow(() -> new EntityNotFoundAppException("Quotation", id));
        if (!EDITABLE.contains(q.getStatus())) {
            throw new BusinessValidationException(
                    "Cannot edit a quotation in status " + q.getStatus());
        }

        applyFieldsFromDto(q, dto);
        rebuildItems(q, dto);
        recomputeTotals(q);

        return toDto(quotationRepo.save(q));
    }

    @Transactional
    @LogAudit(action = "SEND_QUOTATION", entity = "QUOTATION")
    public QuotationDto send(Long id) {
        return transition(id, QuotationStatus.SENT, Set.of(QuotationStatus.DRAFT, QuotationStatus.SENT));
    }

    @Transactional
    @LogAudit(action = "ACCEPT_QUOTATION", entity = "QUOTATION")
    public QuotationDto accept(Long id) {
        return transition(id, QuotationStatus.ACCEPTED, Set.of(QuotationStatus.SENT, QuotationStatus.DRAFT));
    }

    @Transactional
    @LogAudit(action = "REJECT_QUOTATION", entity = "QUOTATION")
    public QuotationDto reject(Long id, String reason) {
        Quotation q = quotationRepo.findById(id)
                .orElseThrow(() -> new EntityNotFoundAppException("Quotation", id));
        if (!Set.of(QuotationStatus.SENT, QuotationStatus.DRAFT).contains(q.getStatus())) {
            throw new BusinessValidationException("Cannot reject a quotation in status " + q.getStatus());
        }
        q.setStatus(QuotationStatus.REJECTED);
        if (reason != null && !reason.isBlank()) {
            q.setNotes(appendNote(q.getNotes(), "Rejected: " + reason));
        }
        return toDto(quotationRepo.save(q));
    }

    @Transactional
    @LogAudit(action = "CANCEL_QUOTATION", entity = "QUOTATION")
    public QuotationDto cancel(Long id) {
        Quotation q = quotationRepo.findById(id)
                .orElseThrow(() -> new EntityNotFoundAppException("Quotation", id));
        if (TERMINAL.contains(q.getStatus())) {
            throw new BusinessValidationException("Cannot cancel a quotation in status " + q.getStatus());
        }
        q.setStatus(QuotationStatus.CANCELLED);
        return toDto(quotationRepo.save(q));
    }

    /**
     * Converts a quotation to a DRAFT {@link Sale}. Idempotent: if the quotation
     * is already CONVERTED, throws — the user cannot double-convert. The user
     * completes the resulting sale draft in the normal Sales flow.
     */
    @Transactional
    @LogAudit(action = "CONVERT_QUOTATION", entity = "QUOTATION")
    public QuotationDto convertToSale(Long id) {
        Quotation q = quotationRepo.findById(id)
                .orElseThrow(() -> new EntityNotFoundAppException("Quotation", id));

        if (q.getStatus() == QuotationStatus.CONVERTED) {
            throw new BusinessValidationException("Quotation " + q.getQuotationNo()
                    + " has already been converted to sale #" + (q.getConvertedToSale() != null ? q.getConvertedToSale().getId() : "?"));
        }
        if (TERMINAL.contains(q.getStatus())) {
            throw new BusinessValidationException("Cannot convert a quotation in status " + q.getStatus());
        }

        SaleDto saleDto = buildSaleDraftDto(q);
        SaleDto saved = saleService.saveOrUpdateDraft(saleDto);

        Sale sale = saleRepo.findById(saved.getId())
                .orElseThrow(() -> new IllegalStateException("Draft Sale " + saved.getId() + " vanished immediately after save"));
        q.setConvertedToSale(sale);
        q.setStatus(QuotationStatus.CONVERTED);
        Quotation persisted = quotationRepo.save(q);

        logger.info("Converted quotation {} → draft Sale id={}", q.getQuotationNo(), sale.getId());
        return toDto(persisted);
    }

    // ─── Reads ──────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public QuotationDto get(Long id) {
        return toDto(quotationRepo.findById(id)
                .orElseThrow(() -> new EntityNotFoundAppException("Quotation", id)));
    }

    @Transactional(readOnly = true)
    public Page<QuotationDto> list(Pageable pageable, QuotationStatus status, Long customerId) {
        Long shopId = TenantContext.getCurrentShopId();
        Page<Quotation> page;
        if (status != null) {
            page = quotationRepo.findByShopIdAndStatus(shopId, status, pageable);
        } else if (customerId != null) {
            page = quotationRepo.findByShopIdAndCustomer_Id(shopId, customerId, pageable);
        } else {
            page = quotationRepo.findAllByShopId(shopId, pageable);
        }
        return page.map(this::toDto);
    }

    /**
     * Bulk-flip quotations that are past their expiry date to
     * {@link QuotationStatus#EXPIRED}. Called by the scheduled expiry job.
     */
    @Transactional
    public int expireStaleQuotations() {
        int updated = quotationRepo.expireStaleQuotations(java.time.LocalDate.now());
        if (updated > 0) {
            logger.info("Expired {} quotations past their expiry date", updated);
        }
        return updated;
    }

    // ─── Helpers ────────────────────────────────────────────────────────

    private QuotationDto transition(Long id, QuotationStatus to, Set<QuotationStatus> allowedFrom) {
        Quotation q = quotationRepo.findById(id)
                .orElseThrow(() -> new EntityNotFoundAppException("Quotation", id));
        if (!allowedFrom.contains(q.getStatus())) {
            throw new BusinessValidationException(
                    "Cannot transition quotation from " + q.getStatus() + " to " + to);
        }
        q.setStatus(to);
        return toDto(quotationRepo.save(q));
    }

    private void applyFieldsFromDto(Quotation q, QuotationDto dto) {
        if (dto.getCustomerId() != null) {
            Customer c = customerRepo.findById(dto.getCustomerId())
                    .orElseThrow(() -> new EntityNotFoundAppException("Customer", dto.getCustomerId()));
            q.setCustomer(c);
        } else {
            q.setCustomer(null);
        }
        q.setExpiryDate(dto.getExpiryDate());
        q.setNotes(dto.getNotes());
        q.setTerms(dto.getTerms());
        q.setIsGstRequired(dto.getIsGstRequired() != null ? dto.getIsGstRequired() : Boolean.TRUE);
        q.setInvoiceDiscount(nz(dto.getInvoiceDiscount()));
        q.setShippingCharges(nz(dto.getShippingCharges()));
        q.setOtherCharges(nz(dto.getOtherCharges()));
    }

    private void rebuildItems(Quotation q, QuotationDto dto) {
        q.getItems().clear();
        if (dto.getItems() == null) return;

        Shop shop = q.getShop();
        Customer customer = q.getCustomer();
        boolean intraState = customer != null
                && shop != null
                && shop.getState() != null
                && customer.getState() != null
                && shop.getState().equalsIgnoreCase(customer.getState());

        for (QuotationItemDto d : dto.getItems()) {
            QuotationItem item = new QuotationItem();
            item.setShop(shop);

            ItemVariant variant = null;
            if (d.getItemVariantId() != null) {
                variant = variantRepo.findById(d.getItemVariantId())
                        .orElseThrow(() -> new EntityNotFoundAppException("Item Variant", d.getItemVariantId()));
                item.setItemVariant(variant);
                item.setItemName(variant.getItem() != null && variant.getItem().getName() != null
                        ? variant.getItem().getName() : d.getItemName());
                item.setHsnSac(variant.getHsn());
                item.setUnit(variant.getUnit());
            } else {
                String name = (d.getCustomItemName() != null && !d.getCustomItemName().isBlank())
                        ? d.getCustomItemName() : d.getItemName();
                item.setCustomItemName(name);
                item.setCustomDescription(d.getCustomDescription());
                item.setCustomHsnSac(d.getCustomHsnSac());
                item.setCustomUnit(d.getCustomUnit());
                item.setItemName(name);
                item.setHsnSac(d.getCustomHsnSac());
                item.setUnit(d.getCustomUnit());
            }

            BigDecimal qty = d.getQty();
            BigDecimal unitPrice = d.getUnitPrice();
            BigDecimal discount = nz(d.getDiscount());
            item.setQty(qty);
            item.setUnitPrice(unitPrice);
            item.setDiscount(discount);

            BigDecimal taxable = qty.multiply(unitPrice).subtract(discount).max(BigDecimal.ZERO);
            item.setTaxableValue(taxable);

            Integer gstRate = variant != null && variant.getGstRate() != null
                    ? variant.getGstRate()
                    : (d.getGstRate() != null ? d.getGstRate() : 0);
            item.setGstRate(gstRate);

            if (Boolean.TRUE.equals(q.getIsGstRequired()) && gstRate > 0) {
                item.setGstType(GSTType.fromRate(gstRate));
                BigDecimal totalTax = taxable.multiply(BigDecimal.valueOf(gstRate))
                        .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                if (intraState) {
                    BigDecimal half = totalTax.divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
                    item.setCgstAmt(half);
                    item.setSgstAmt(totalTax.subtract(half));
                    item.setIgstAmt(BigDecimal.ZERO);
                } else {
                    item.setCgstAmt(BigDecimal.ZERO);
                    item.setSgstAmt(BigDecimal.ZERO);
                    item.setIgstAmt(totalTax);
                }
            } else {
                item.setGstType(GSTType.GST_0);
                item.setCgstAmt(BigDecimal.ZERO);
                item.setSgstAmt(BigDecimal.ZERO);
                item.setIgstAmt(BigDecimal.ZERO);
            }

            item.setLineTotal(taxable.add(item.getCgstAmt()).add(item.getSgstAmt()).add(item.getIgstAmt()));
            q.addItem(item);
        }
    }

    private void recomputeTotals(Quotation q) {
        BigDecimal taxable = BigDecimal.ZERO;
        BigDecimal cgst = BigDecimal.ZERO;
        BigDecimal sgst = BigDecimal.ZERO;
        BigDecimal igst = BigDecimal.ZERO;
        for (QuotationItem it : q.getItems()) {
            taxable = taxable.add(nz(it.getTaxableValue()));
            cgst = cgst.add(nz(it.getCgstAmt()));
            sgst = sgst.add(nz(it.getSgstAmt()));
            igst = igst.add(nz(it.getIgstAmt()));
        }
        q.setTotalTaxableAmount(taxable);
        q.setTotalCgst(cgst);
        q.setTotalSgst(sgst);
        q.setTotalIgst(igst);
        BigDecimal grand = taxable.add(cgst).add(sgst).add(igst)
                .subtract(nz(q.getInvoiceDiscount()))
                .add(nz(q.getShippingCharges()))
                .add(nz(q.getOtherCharges()))
                .max(BigDecimal.ZERO);
        q.setTotalAmount(grand.setScale(2, RoundingMode.HALF_UP));
    }

    private SaleDto buildSaleDraftDto(Quotation q) {
        SaleDto sd = new SaleDto();
        if (q.getCustomer() != null) {
            CustomerDto cd = new CustomerDto();
            cd.setId(q.getCustomer().getId());
            cd.setName(q.getCustomer().getName());
            sd.setCustomer(cd);
        }
        sd.setDate(q.getQuotationDate());
        sd.setIsGstRequired(q.getIsGstRequired());
        sd.setInvoiceDiscount(q.getInvoiceDiscount());
        sd.setShippingCharges(q.getShippingCharges());
        sd.setOtherCharges(q.getOtherCharges());
        // SaleDto has no notes field yet — origin quotation is tracked via
        // Quotation.convertedToSaleId FK instead of a free-text note.

        List<SaleItemDto> items = new ArrayList<>();
        for (QuotationItem it : q.getItems()) {
            SaleItemDto s = new SaleItemDto();
            s.setItemVariantId(it.getItemVariant() != null ? it.getItemVariant().getId() : null);
            s.setItemName(it.getItemName());
            s.setQty(it.getQty());
            s.setUnitPrice(it.getUnitPrice());
            s.setDiscount(it.getDiscount() != null ? it.getDiscount() : BigDecimal.ZERO);
            s.setGstRate(it.getGstRate() != null ? it.getGstRate() : 0);
            if (it.getItemVariant() == null) {
                s.setCustomItemName(it.getCustomItemName());
                s.setCustomDescription(it.getCustomDescription());
                s.setCustomHsnSac(it.getCustomHsnSac());
                s.setCustomUnit(it.getCustomUnit());
            }
            items.add(s);
        }
        sd.setItems(items);
        return sd;
    }

    private String appendNote(String existing, String append) {
        if (append == null || append.isBlank()) return existing;
        if (existing == null || existing.isBlank()) return append;
        return existing + " | " + append;
    }

    private static BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }

    // ─── Mapping ────────────────────────────────────────────────────────

    private QuotationDto toDto(Quotation q) {
        QuotationDto dto = new QuotationDto();
        dto.setId(q.getId());
        dto.setQuotationNo(q.getQuotationNo());
        if (q.getCustomer() != null) {
            CustomerDto cd = new CustomerDto();
            cd.setId(q.getCustomer().getId());
            cd.setName(q.getCustomer().getName());
            cd.setPhone(q.getCustomer().getPhone());
            dto.setCustomer(cd);
            dto.setCustomerId(q.getCustomer().getId());
        }
        dto.setQuotationDate(q.getQuotationDate());
        dto.setExpiryDate(q.getExpiryDate());
        dto.setTotalTaxableAmount(q.getTotalTaxableAmount());
        dto.setTotalCgst(q.getTotalCgst());
        dto.setTotalSgst(q.getTotalSgst());
        dto.setTotalIgst(q.getTotalIgst());
        dto.setInvoiceDiscount(q.getInvoiceDiscount());
        dto.setShippingCharges(q.getShippingCharges());
        dto.setOtherCharges(q.getOtherCharges());
        dto.setTotalAmount(q.getTotalAmount());
        dto.setIsGstRequired(q.getIsGstRequired());
        dto.setNotes(q.getNotes());
        dto.setTerms(q.getTerms());
        dto.setStatus(q.getStatus());
        if (q.getConvertedToSale() != null) {
            dto.setConvertedToSaleId(q.getConvertedToSale().getId());
            dto.setConvertedInvoiceNo(q.getConvertedToSale().getInvoiceNo());
        }

        List<QuotationItemDto> its = new ArrayList<>();
        for (QuotationItem it : q.getItems()) {
            QuotationItemDto d = new QuotationItemDto();
            d.setId(it.getId());
            d.setItemVariantId(it.getItemVariant() != null ? it.getItemVariant().getId() : null);
            d.setItemName(it.getItemName());
            d.setHsnSac(it.getHsnSac());
            d.setUnit(it.getUnit());
            d.setQty(it.getQty());
            d.setUnitPrice(it.getUnitPrice());
            d.setDiscount(it.getDiscount());
            d.setGstRate(it.getGstRate());
            d.setTaxableValue(it.getTaxableValue());
            d.setCgstAmt(it.getCgstAmt());
            d.setSgstAmt(it.getSgstAmt());
            d.setIgstAmt(it.getIgstAmt());
            d.setLineTotal(it.getLineTotal());
            d.setCustomItemName(it.getCustomItemName());
            d.setCustomDescription(it.getCustomDescription());
            d.setCustomHsnSac(it.getCustomHsnSac());
            d.setCustomUnit(it.getCustomUnit());
            its.add(d);
        }
        dto.setItems(its);
        return dto;
    }
}
