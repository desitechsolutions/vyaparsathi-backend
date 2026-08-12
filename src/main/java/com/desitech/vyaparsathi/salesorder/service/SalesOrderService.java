package com.desitech.vyaparsathi.salesorder.service;

import com.desitech.vyaparsathi.common.annotations.LogAudit;
import com.desitech.vyaparsathi.common.configs.TenantContext;
import com.desitech.vyaparsathi.common.exception.BusinessValidationException;
import com.desitech.vyaparsathi.common.exception.EntityNotFoundAppException;
import com.desitech.vyaparsathi.customer.dto.CustomerDto;
import com.desitech.vyaparsathi.customer.entity.Customer;
import com.desitech.vyaparsathi.customer.repository.CustomerRepository;
import com.desitech.vyaparsathi.inventory.entity.ItemVariant;
import com.desitech.vyaparsathi.inventory.repository.ItemVariantRepository;
import com.desitech.vyaparsathi.quotation.entity.Quotation;
import com.desitech.vyaparsathi.quotation.repository.QuotationRepository;
import com.desitech.vyaparsathi.sales.dto.SaleDto;
import com.desitech.vyaparsathi.sales.dto.SaleItemDto;
import com.desitech.vyaparsathi.sales.entity.Sale;
import com.desitech.vyaparsathi.sales.enums.GSTType;
import com.desitech.vyaparsathi.sales.repository.SaleRepository;
import com.desitech.vyaparsathi.sales.service.SaleService;
import com.desitech.vyaparsathi.salesorder.dto.ConvertToSaleRequest;
import com.desitech.vyaparsathi.salesorder.dto.SalesOrderDto;
import com.desitech.vyaparsathi.salesorder.dto.SalesOrderItemDto;
import com.desitech.vyaparsathi.salesorder.entity.SalesOrder;
import com.desitech.vyaparsathi.salesorder.entity.SalesOrderItem;
import com.desitech.vyaparsathi.salesorder.entity.StockReservation;
import com.desitech.vyaparsathi.salesorder.enums.SalesOrderStatus;
import com.desitech.vyaparsathi.salesorder.repository.SalesOrderRepository;
import com.desitech.vyaparsathi.salesorder.repository.StockReservationRepository;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Sales Order lifecycle and stock-reservation service.
 *
 * <p>Key operations:
 * <ul>
 *   <li>{@link #create}, {@link #update} — editable while DRAFT.</li>
 *   <li>{@link #approve} — transitions DRAFT → APPROVED and creates a
 *       {@link StockReservation} for each catalog line.</li>
 *   <li>{@link #cancel} — releases all reservations for the order.</li>
 *   <li>{@link #convertToSale} — partial or full conversion. Consumes
 *       reservations proportionally, decrements physical stock via a normal
 *       Sale draft (which the user then completes).</li>
 *   <li>{@link #createFromQuotation} — bootstrap an SO from an accepted quote.</li>
 * </ul>
 */
@Service
public class SalesOrderService {

    private static final Logger logger = LoggerFactory.getLogger(SalesOrderService.class);

    private static final Set<SalesOrderStatus> EDITABLE = EnumSet.of(SalesOrderStatus.DRAFT);
    private static final Set<SalesOrderStatus> TERMINAL =
            EnumSet.of(SalesOrderStatus.FULFILLED, SalesOrderStatus.CANCELLED);

    private final SalesOrderRepository orderRepo;
    private final SalesOrderNumberService numberService;
    private final StockReservationRepository reservationRepo;
    private final CustomerRepository customerRepo;
    private final ItemVariantRepository variantRepo;
    private final ShopRepository shopRepo;
    private final SaleService saleService;
    private final SaleRepository saleRepo;
    private final QuotationRepository quotationRepo;

    public SalesOrderService(SalesOrderRepository orderRepo,
                             SalesOrderNumberService numberService,
                             StockReservationRepository reservationRepo,
                             CustomerRepository customerRepo,
                             ItemVariantRepository variantRepo,
                             ShopRepository shopRepo,
                             SaleService saleService,
                             SaleRepository saleRepo,
                             QuotationRepository quotationRepo) {
        this.orderRepo = orderRepo;
        this.numberService = numberService;
        this.reservationRepo = reservationRepo;
        this.customerRepo = customerRepo;
        this.variantRepo = variantRepo;
        this.shopRepo = shopRepo;
        this.saleService = saleService;
        this.saleRepo = saleRepo;
        this.quotationRepo = quotationRepo;
    }

    // ─── CRUD ───────────────────────────────────────────────────────────

    @Transactional
    @LogAudit(action = "CREATE_SALES_ORDER", entity = "SALES_ORDER")
    public SalesOrderDto create(SalesOrderDto dto) {
        Long shopId = TenantContext.getCurrentShopId();
        Shop shop = shopRepo.findById(shopId)
                .orElseThrow(() -> new EntityNotFoundAppException("Shop", shopId));

        SalesOrder so = new SalesOrder();
        so.setShop(shop);
        so.setStatus(SalesOrderStatus.DRAFT);
        so.setOrderDate(dto.getOrderDate() != null ? dto.getOrderDate() : LocalDateTime.now());
        so.setOrderNo(numberService.nextOrderNumber(shopId, so.getOrderDate().toLocalDate()));

        applyFieldsFromDto(so, dto);
        rebuildItems(so, dto);
        recomputeTotals(so);

        SalesOrder saved = orderRepo.save(so);
        logger.info("Created sales order {} for shopId={}", saved.getOrderNo(), shopId);
        return toDto(saved);
    }

    @Transactional
    @LogAudit(action = "UPDATE_SALES_ORDER", entity = "SALES_ORDER")
    public SalesOrderDto update(Long id, SalesOrderDto dto) {
        SalesOrder so = orderRepo.findById(id)
                .orElseThrow(() -> new EntityNotFoundAppException("Sales Order", id));
        if (!EDITABLE.contains(so.getStatus())) {
            throw new BusinessValidationException("Cannot edit a sales order in status " + so.getStatus());
        }
        applyFieldsFromDto(so, dto);
        rebuildItems(so, dto);
        recomputeTotals(so);
        return toDto(orderRepo.save(so));
    }

    // ─── State transitions ────────────────────────────────────────────────

    @Transactional
    @LogAudit(action = "APPROVE_SALES_ORDER", entity = "SALES_ORDER")
    public SalesOrderDto approve(Long id) {
        SalesOrder so = orderRepo.findById(id)
                .orElseThrow(() -> new EntityNotFoundAppException("Sales Order", id));
        if (so.getStatus() != SalesOrderStatus.DRAFT) {
            throw new BusinessValidationException("Only DRAFT sales orders can be approved");
        }

        // Create one StockReservation per catalog line
        for (SalesOrderItem it : so.getItems()) {
            if (it.getItemVariant() == null) continue;   // custom lines don't reserve stock
            if (it.getQty() == null || it.getQty().signum() <= 0) continue;

            StockReservation res = new StockReservation();
            res.setShop(so.getShop());
            res.setSalesOrder(so);
            res.setSalesOrderItem(it);
            res.setItemVariant(it.getItemVariant());
            res.setReservedQty(it.getQty());
            reservationRepo.save(res);
        }

        so.setStatus(SalesOrderStatus.APPROVED);
        SalesOrder saved = orderRepo.save(so);
        logger.info("Approved sales order {} — {} reservation(s) created",
                saved.getOrderNo(), saved.getItems().size());
        return toDto(saved);
    }

    @Transactional
    @LogAudit(action = "CANCEL_SALES_ORDER", entity = "SALES_ORDER")
    public SalesOrderDto cancel(Long id) {
        SalesOrder so = orderRepo.findById(id)
                .orElseThrow(() -> new EntityNotFoundAppException("Sales Order", id));
        if (TERMINAL.contains(so.getStatus())) {
            throw new BusinessValidationException("Cannot cancel a sales order in status " + so.getStatus());
        }
        // Release reservations
        reservationRepo.deleteBySalesOrder_Id(so.getId());
        so.setStatus(SalesOrderStatus.CANCELLED);
        return toDto(orderRepo.save(so));
    }

    /**
     * Converts (all or part of) a Sales Order into a DRAFT Sale. Reservations
     * are consumed for the fulfilled quantity. Multiple partial conversions
     * are supported — the SO status transitions through PARTIALLY_FULFILLED and
     * finally FULFILLED once every line's fulfilled_qty >= qty.
     */
    @Transactional
    @LogAudit(action = "CONVERT_SALES_ORDER", entity = "SALES_ORDER")
    public SalesOrderDto convertToSale(Long id, ConvertToSaleRequest request) {
        SalesOrder so = orderRepo.findById(id)
                .orElseThrow(() -> new EntityNotFoundAppException("Sales Order", id));

        if (so.getStatus() != SalesOrderStatus.APPROVED
                && so.getStatus() != SalesOrderStatus.PARTIALLY_FULFILLED) {
            throw new BusinessValidationException(
                    "Sales Order must be APPROVED or PARTIALLY_FULFILLED to convert; current status: " + so.getStatus());
        }

        // Build a map: SalesOrderItemId → qty to fulfill this pass.
        // Empty request lines = fulfill remaining qty on every line.
        Map<Long, BigDecimal> toFulfill = new HashMap<>();
        if (request == null || request.getLines() == null || request.getLines().isEmpty()) {
            for (SalesOrderItem it : so.getItems()) {
                BigDecimal remaining = it.getRemainingQty();
                if (remaining.signum() > 0) toFulfill.put(it.getId(), remaining);
            }
        } else {
            for (ConvertToSaleRequest.LineFulfillment l : request.getLines()) {
                if (l.getSalesOrderItemId() == null || l.getQty() == null) continue;
                if (l.getQty().signum() <= 0) continue;
                toFulfill.merge(l.getSalesOrderItemId(), l.getQty(), BigDecimal::add);
            }
        }
        if (toFulfill.isEmpty()) {
            throw new BusinessValidationException("Nothing to convert — no positive qty requested");
        }

        // Validate + snapshot each requested item as a SaleItemDto for the draft.
        List<SaleItemDto> saleItems = new ArrayList<>();
        for (SalesOrderItem it : so.getItems()) {
            BigDecimal req = toFulfill.get(it.getId());
            if (req == null) continue;
            BigDecimal remaining = it.getRemainingQty();
            if (req.compareTo(remaining) > 0) {
                throw new BusinessValidationException(
                        "Requested " + req + " exceeds remaining " + remaining + " on item " + it.getItemName());
            }

            SaleItemDto s = new SaleItemDto();
            s.setItemVariantId(it.getItemVariant() != null ? it.getItemVariant().getId() : null);
            s.setItemName(it.getItemName());
            s.setQty(req);
            s.setUnitPrice(it.getUnitPrice());
            s.setDiscount(it.getDiscount() != null ? it.getDiscount() : BigDecimal.ZERO);
            s.setGstRate(it.getGstRate() != null ? it.getGstRate() : 0);
            if (it.getItemVariant() == null) {
                s.setCustomItemName(it.getCustomItemName());
                s.setCustomDescription(it.getCustomDescription());
                s.setCustomHsnSac(it.getCustomHsnSac());
                s.setCustomUnit(it.getCustomUnit());
            }
            saleItems.add(s);
        }

        // Create the draft Sale
        SaleDto sd = new SaleDto();
        if (so.getCustomer() != null) {
            CustomerDto cd = new CustomerDto();
            cd.setId(so.getCustomer().getId());
            cd.setName(so.getCustomer().getName());
            sd.setCustomer(cd);
        }
        sd.setDate(LocalDateTime.now());
        sd.setIsGstRequired(so.getIsGstRequired());
        sd.setInvoiceDiscount(so.getInvoiceDiscount());
        sd.setShippingCharges(so.getShippingCharges());
        sd.setOtherCharges(so.getOtherCharges());
        sd.setItems(saleItems);
        SaleDto savedSale = saleService.saveOrUpdateDraft(sd);

        Sale sale = saleRepo.findById(savedSale.getId())
                .orElseThrow(() -> new IllegalStateException("Draft Sale " + savedSale.getId() + " vanished"));

        // Advance fulfilled_qty on each SO line + consume matching reservation qty
        for (SalesOrderItem it : so.getItems()) {
            BigDecimal req = toFulfill.get(it.getId());
            if (req == null) continue;
            BigDecimal newFulfilled = (it.getFulfilledQty() != null ? it.getFulfilledQty() : BigDecimal.ZERO).add(req);
            it.setFulfilledQty(newFulfilled);

            // Consume from the item's reservation row(s)
            List<StockReservation> reservations = reservationRepo.findBySalesOrderItem_Id(it.getId());
            BigDecimal remaining = req;
            for (StockReservation r : reservations) {
                if (remaining.signum() <= 0) break;
                BigDecimal take = r.getReservedQty().min(remaining);
                BigDecimal after = r.getReservedQty().subtract(take);
                if (after.signum() <= 0) {
                    reservationRepo.delete(r);
                } else {
                    r.setReservedQty(after);
                    reservationRepo.save(r);
                }
                remaining = remaining.subtract(take);
            }
        }

        // Advance SO status
        boolean anyOutstanding = so.getItems().stream()
                .anyMatch(it -> it.getRemainingQty().signum() > 0);
        so.setStatus(anyOutstanding ? SalesOrderStatus.PARTIALLY_FULFILLED : SalesOrderStatus.FULFILLED);
        SalesOrder saved = orderRepo.save(so);

        logger.info("Converted SO {} → Sale draft id={} (SO status: {})",
                saved.getOrderNo(), sale.getId(), saved.getStatus());
        SalesOrderDto dto = toDto(saved);
        dto.setCreatedSaleId(sale.getId());
        return dto;
    }

    /**
     * Bootstraps a Sales Order from an accepted Quotation. The Quotation stays
     * intact — a separate {@code convertToSale} on the Quotation is only for
     * the "skip the SO step" workflow.
     */
    @Transactional
    @LogAudit(action = "CREATE_SO_FROM_QUOTATION", entity = "SALES_ORDER")
    public SalesOrderDto createFromQuotation(Long quotationId) {
        Quotation q = quotationRepo.findById(quotationId)
                .orElseThrow(() -> new EntityNotFoundAppException("Quotation", quotationId));

        SalesOrderDto dto = new SalesOrderDto();
        dto.setCustomerId(q.getCustomer() != null ? q.getCustomer().getId() : null);
        dto.setOrderDate(LocalDateTime.now());
        dto.setIsGstRequired(q.getIsGstRequired());
        dto.setInvoiceDiscount(q.getInvoiceDiscount());
        dto.setShippingCharges(q.getShippingCharges());
        dto.setOtherCharges(q.getOtherCharges());
        dto.setNotes(q.getNotes());
        dto.setTerms(q.getTerms());

        List<SalesOrderItemDto> its = new ArrayList<>();
        q.getItems().forEach(qi -> {
            SalesOrderItemDto d = new SalesOrderItemDto();
            d.setItemVariantId(qi.getItemVariant() != null ? qi.getItemVariant().getId() : null);
            d.setItemName(qi.getItemName());
            d.setQty(qi.getQty());
            d.setUnitPrice(qi.getUnitPrice());
            d.setDiscount(qi.getDiscount());
            d.setGstRate(qi.getGstRate());
            d.setCustomItemName(qi.getCustomItemName());
            d.setCustomDescription(qi.getCustomDescription());
            d.setCustomHsnSac(qi.getCustomHsnSac());
            d.setCustomUnit(qi.getCustomUnit());
            its.add(d);
        });
        dto.setItems(its);

        SalesOrderDto created = create(dto);

        // Link the SO back to the quotation (bypass repository lookup since we just created it)
        SalesOrder so = orderRepo.findById(created.getId()).orElseThrow();
        so.setQuotation(q);
        orderRepo.save(so);
        created.setQuotationId(q.getId());
        created.setQuotationNo(q.getQuotationNo());
        return created;
    }

    // ─── Reads ──────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public SalesOrderDto get(Long id) {
        return toDto(orderRepo.findById(id)
                .orElseThrow(() -> new EntityNotFoundAppException("Sales Order", id)));
    }

    @Transactional(readOnly = true)
    public Page<SalesOrderDto> list(Pageable pageable, SalesOrderStatus status, Long customerId) {
        Long shopId = TenantContext.getCurrentShopId();
        Page<SalesOrder> page;
        if (status != null) {
            page = orderRepo.findByShopIdAndStatus(shopId, status, pageable);
        } else if (customerId != null) {
            page = orderRepo.findByShopIdAndCustomer_Id(shopId, customerId, pageable);
        } else {
            page = orderRepo.findAllByShopId(shopId, pageable);
        }
        return page.map(this::toDto);
    }

    // ─── Helpers ────────────────────────────────────────────────────────

    private void applyFieldsFromDto(SalesOrder so, SalesOrderDto dto) {
        if (dto.getCustomerId() != null) {
            Customer c = customerRepo.findById(dto.getCustomerId())
                    .orElseThrow(() -> new EntityNotFoundAppException("Customer", dto.getCustomerId()));
            so.setCustomer(c);
        } else {
            so.setCustomer(null);
        }
        so.setExpectedDeliveryDate(dto.getExpectedDeliveryDate());
        so.setNotes(dto.getNotes());
        so.setTerms(dto.getTerms());
        so.setIsGstRequired(dto.getIsGstRequired() != null ? dto.getIsGstRequired() : Boolean.TRUE);
        so.setInvoiceDiscount(nz(dto.getInvoiceDiscount()));
        so.setShippingCharges(nz(dto.getShippingCharges()));
        so.setOtherCharges(nz(dto.getOtherCharges()));
    }

    private void rebuildItems(SalesOrder so, SalesOrderDto dto) {
        so.getItems().clear();
        if (dto.getItems() == null) return;

        Shop shop = so.getShop();
        Customer customer = so.getCustomer();
        boolean intraState = customer != null && shop != null
                && shop.getState() != null && customer.getState() != null
                && shop.getState().equalsIgnoreCase(customer.getState());

        for (SalesOrderItemDto d : dto.getItems()) {
            SalesOrderItem item = new SalesOrderItem();
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
            item.setFulfilledQty(BigDecimal.ZERO);
            item.setUnitPrice(unitPrice);
            item.setDiscount(discount);

            BigDecimal taxable = qty.multiply(unitPrice).subtract(discount).max(BigDecimal.ZERO);
            item.setTaxableValue(taxable);

            Integer gstRate = variant != null && variant.getGstRate() != null
                    ? variant.getGstRate()
                    : (d.getGstRate() != null ? d.getGstRate() : 0);
            item.setGstRate(gstRate);

            if (Boolean.TRUE.equals(so.getIsGstRequired()) && gstRate > 0) {
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
            so.addItem(item);
        }
    }

    private void recomputeTotals(SalesOrder so) {
        BigDecimal taxable = BigDecimal.ZERO;
        BigDecimal cgst = BigDecimal.ZERO;
        BigDecimal sgst = BigDecimal.ZERO;
        BigDecimal igst = BigDecimal.ZERO;
        for (SalesOrderItem it : so.getItems()) {
            taxable = taxable.add(nz(it.getTaxableValue()));
            cgst = cgst.add(nz(it.getCgstAmt()));
            sgst = sgst.add(nz(it.getSgstAmt()));
            igst = igst.add(nz(it.getIgstAmt()));
        }
        so.setTotalTaxableAmount(taxable);
        so.setTotalCgst(cgst);
        so.setTotalSgst(sgst);
        so.setTotalIgst(igst);
        BigDecimal grand = taxable.add(cgst).add(sgst).add(igst)
                .subtract(nz(so.getInvoiceDiscount()))
                .add(nz(so.getShippingCharges()))
                .add(nz(so.getOtherCharges()))
                .max(BigDecimal.ZERO);
        so.setTotalAmount(grand.setScale(2, RoundingMode.HALF_UP));
    }

    private static BigDecimal nz(BigDecimal v) { return v != null ? v : BigDecimal.ZERO; }

    // ─── Mapping ────────────────────────────────────────────────────────

    private SalesOrderDto toDto(SalesOrder so) {
        SalesOrderDto dto = new SalesOrderDto();
        dto.setId(so.getId());
        dto.setOrderNo(so.getOrderNo());
        if (so.getCustomer() != null) {
            CustomerDto cd = new CustomerDto();
            cd.setId(so.getCustomer().getId());
            cd.setName(so.getCustomer().getName());
            cd.setPhone(so.getCustomer().getPhone());
            dto.setCustomer(cd);
            dto.setCustomerId(so.getCustomer().getId());
        }
        dto.setOrderDate(so.getOrderDate());
        dto.setExpectedDeliveryDate(so.getExpectedDeliveryDate());
        dto.setTotalTaxableAmount(so.getTotalTaxableAmount());
        dto.setTotalCgst(so.getTotalCgst());
        dto.setTotalSgst(so.getTotalSgst());
        dto.setTotalIgst(so.getTotalIgst());
        dto.setInvoiceDiscount(so.getInvoiceDiscount());
        dto.setShippingCharges(so.getShippingCharges());
        dto.setOtherCharges(so.getOtherCharges());
        dto.setTotalAmount(so.getTotalAmount());
        dto.setIsGstRequired(so.getIsGstRequired());
        dto.setNotes(so.getNotes());
        dto.setTerms(so.getTerms());
        dto.setStatus(so.getStatus());
        if (so.getQuotation() != null) {
            dto.setQuotationId(so.getQuotation().getId());
            dto.setQuotationNo(so.getQuotation().getQuotationNo());
        }

        List<SalesOrderItemDto> its = new ArrayList<>();
        for (SalesOrderItem it : so.getItems()) {
            SalesOrderItemDto d = new SalesOrderItemDto();
            d.setId(it.getId());
            d.setItemVariantId(it.getItemVariant() != null ? it.getItemVariant().getId() : null);
            d.setItemName(it.getItemName());
            d.setHsnSac(it.getHsnSac());
            d.setUnit(it.getUnit());
            d.setQty(it.getQty());
            d.setFulfilledQty(it.getFulfilledQty());
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
