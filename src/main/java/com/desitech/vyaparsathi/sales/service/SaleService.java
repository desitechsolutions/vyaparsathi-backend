package com.desitech.vyaparsathi.sales.service;

import com.desitech.vyaparsathi.accounting.entity.CreditNote;
import com.desitech.vyaparsathi.accounting.service.CreditNoteService;
import com.desitech.vyaparsathi.common.annotations.CheckSubscriptionLimit;
import com.desitech.vyaparsathi.common.annotations.LogAudit;
import com.desitech.vyaparsathi.audit.helper.AuditHelper;
import com.desitech.vyaparsathi.auth.security.JwtUtil;
import com.desitech.vyaparsathi.changelog.service.ChangeLogService;
import com.desitech.vyaparsathi.common.configs.TenantContext;
import com.desitech.vyaparsathi.compliance.exception.LockedPeriodException;
import com.desitech.vyaparsathi.compliance.service.PeriodLockService;
import com.desitech.vyaparsathi.common.exception.BusinessValidationException;
import com.desitech.vyaparsathi.common.exception.EntityNotFoundAppException;
import com.desitech.vyaparsathi.common.exception.InsufficientStockException;
import com.desitech.vyaparsathi.customer.dto.CustomerLedgerDto;
import com.desitech.vyaparsathi.customer.entity.Customer;
import com.desitech.vyaparsathi.customer.entity.CustomerLedgerType;
import com.desitech.vyaparsathi.customer.repository.CustomerRepository;
import com.desitech.vyaparsathi.customer.service.CustomerLedgerService;
import com.desitech.vyaparsathi.delivery.dto.DeliveryDTO;
import com.desitech.vyaparsathi.delivery.mapper.DeliveryMapper;
import com.desitech.vyaparsathi.delivery.repository.DeliveryRepository;
import com.desitech.vyaparsathi.delivery.service.DeliveryService;
import com.desitech.vyaparsathi.inventory.dto.StockAdjustmentDto;
import com.desitech.vyaparsathi.inventory.entity.ItemVariant;
import com.desitech.vyaparsathi.inventory.repository.ItemVariantRepository;
import com.desitech.vyaparsathi.inventory.service.StockService;
import com.desitech.vyaparsathi.payment.dto.BulkPaymentRequest;
import com.desitech.vyaparsathi.payment.dto.PaymentDto;
import com.desitech.vyaparsathi.payment.enums.PaymentMethod;
import com.desitech.vyaparsathi.payment.enums.PaymentSourceType;
import com.desitech.vyaparsathi.payment.service.PaymentService;
import com.desitech.vyaparsathi.common.enums.SupplyType;
import com.desitech.vyaparsathi.sales.enums.GSTType;
import com.desitech.vyaparsathi.sales.dto.SaleDto;
import com.desitech.vyaparsathi.sales.dto.SaleDueDto;
import com.desitech.vyaparsathi.sales.dto.SaleItemDto;
import com.desitech.vyaparsathi.sales.dto.SaleReturnDto;
import com.desitech.vyaparsathi.sales.entity.Sale;
import com.desitech.vyaparsathi.sales.entity.SaleItem;
import com.desitech.vyaparsathi.sales.enums.SaleStatus;
import com.desitech.vyaparsathi.sales.mapper.SaleMapper;
import com.desitech.vyaparsathi.sales.repository.SaleRepository;
import com.desitech.vyaparsathi.invoice.service.InvoiceNumberService;
import com.desitech.vyaparsathi.invoice.service.InvoiceService;
import com.desitech.vyaparsathi.shop.entity.Shop;
import com.desitech.vyaparsathi.shop.repository.ShopRepository;
import com.desitech.vyaparsathi.subscriptions.service.SubscriptionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

import static java.math.BigDecimal.ZERO;

@Service
public class SaleService {

    private static final Logger logger = LoggerFactory.getLogger(SaleService.class);

    @Autowired
    private SaleRepository saleRepository;
    @Autowired
    private CustomerRepository customerRepository;
    @Autowired
    private ShopRepository shopRepository;
    @Autowired
    private StockService stockService;
    @Autowired
    private ChangeLogService changeLogService;
    @Autowired
    private InvoiceService invoiceService;
    @Autowired
    private InvoiceNumberService invoiceNumberService;
    @Autowired
    private SaleMapper mapper;
    @Autowired
    private ItemVariantRepository itemVariantRepository;
    @Autowired
    private PaymentService paymentService;
    @Autowired
    private CustomerLedgerService ledgerService;
    @Autowired
    private DeliveryService deliveryService;
    @Autowired
    private DeliveryRepository deliveryRepository;
    @Autowired
    private JwtUtil jwtUtil;
    @Autowired
    private DeliveryMapper deliveryMapper;

    @Autowired
    private AuditHelper auditHelper;

    @Autowired
    private SubscriptionService subscriptionService;

    @Autowired
    private CreditNoteService creditNoteService;

    @Autowired
    private com.desitech.vyaparsathi.accounting.repository.CreditNoteRepository creditNoteRepository;

    @Autowired
    private com.desitech.vyaparsathi.audit.repository.AuditLogRepository auditLogRepository;

    @Autowired
    private com.desitech.vyaparsathi.gst.service.GstJurisdictionService gstJurisdictionService;

    @Autowired
    private PeriodLockService periodLockService;

    private void checkPeriodLock(LocalDate transactionDate) {
        if (transactionDate != null &&
                periodLockService.isPeriodLocked(TenantContext.getCurrentShopId(), transactionDate)) {
            throw new LockedPeriodException(
                    String.format("%02d-%d", transactionDate.getMonthValue(), transactionDate.getYear()));
        }
    }

    @Transactional
    @LogAudit(action = "CREATE_SALE", entity = "SALE")
    @CheckSubscriptionLimit("SALES")
    public SaleDto createSale(SaleDto dto) {
        // Idempotency guard — client can send an Idempotency-Key to make the POST
        // safe to retry. If we've already processed this key for this tenant,
        // return the original sale instead of creating a duplicate.
        // Aspect-based shopFilter scopes findByIdempotencyKey to the tenant.
        if (dto.getIdempotencyKey() != null && !dto.getIdempotencyKey().isBlank()) {
            Optional<Sale> existing = saleRepository.findByIdempotencyKey(dto.getIdempotencyKey().trim());
            if (existing.isPresent()) {
                Sale s = existing.get();
                logger.info("Idempotent replay of createSale — returning existing sale id={} inv={}",
                        s.getId(), s.getInvoiceNo());
                SaleDto replay = mapper.toDto(s);
                String signedToken = jwtUtil.generateInvoiceToken(s.getId(), s.getInvoiceNo(), s.getShop() != null ? s.getShop().getId() : null);
                replay.setSignedInvoiceUrl("/api/invoices/signed?token=" + signedToken);
                return replay;
            }
        }

        checkPeriodLock(dto.getDate() != null ? dto.getDate().toLocalDate() : LocalDate.now());
        subscriptionService.validateSaleProcessingEntitlement(TenantContext.getCurrentShopId());

        // 1. Fetch Context (Shop and Customer)
        Shop shop = shopRepository.findById(TenantContext.getCurrentShopId())
                .orElseThrow(() -> new EntityNotFoundAppException("Shop", TenantContext.getCurrentShopId()));

        Optional<Customer> customerOpt = Optional.ofNullable(dto.getCustomer())
                .map(c -> customerRepository.findById(c.getId())
                        .orElseThrow(() -> new EntityNotFoundAppException("Customer", c.getId())));
        Customer customer = customerOpt.orElse(null);

        // 2. Process Sale Items and Calculate Taxes
        List<SaleItem> saleItems = new ArrayList<>();
        BigDecimal totalTaxableValue = BigDecimal.ZERO;
        BigDecimal totalGSTAmount = BigDecimal.ZERO;

        // Sale-type resolution — composition-scheme shops legally cannot issue
        // a tax invoice, so BILL_OF_SUPPLY is forced regardless of what the
        // caller sends. See Shop.isCompositionScheme.
        com.desitech.vyaparsathi.sales.enums.SaleType parsedType =
                effectiveSaleType(dto.getSaleType(), shop);
        boolean isBillOfSupply = parsedType == com.desitech.vyaparsathi.sales.enums.SaleType.BILL_OF_SUPPLY;
        boolean gstApplicable = Boolean.TRUE.equals(dto.getIsGstRequired()) && !isBillOfSupply;

        // HSN validation for real invoices — required for e-invoice / GSTR-1.
        if (gstApplicable && parsedType == com.desitech.vyaparsathi.sales.enums.SaleType.INVOICE) {
            validateHsnPresent(dto);
        }

        // ── Pre-loop: read bill-level adjustments and resolve jurisdiction ONCE ──────
        // All lines on one invoice share the same shop/customer jurisdiction. Resolving
        // per-line was redundant; moving it here also enables proportional discount
        // allocation before GST is computed (Section 15(3)(b) CGST Act).
        BigDecimal invoiceDisc = (dto.getInvoiceDiscount() != null && dto.getInvoiceDiscount().compareTo(ZERO) > 0)
                ? dto.getInvoiceDiscount() : ZERO;
        BigDecimal shippingCharges = ZERO;
        BigDecimal otherCharges = (dto.getOtherCharges() != null && dto.getOtherCharges().compareTo(ZERO) > 0)
                ? dto.getOtherCharges() : ZERO;
        // Include shippingCharges in total only when collected by the shop (SHOP = store pays/bills)
        if (dto.getDelivery() != null
                && com.desitech.vyaparsathi.delivery.enums.DeliveryPaidBy.SHOP
                        .equals(dto.getDelivery().getDeliveryPaidBy())) {
            shippingCharges = (dto.getShippingCharges() != null) ? dto.getShippingCharges() : ZERO;
        }
        String shopCode     = gstJurisdictionService.resolveStateCode(shop).orElse(null);
        String customerCode = gstJurisdictionService.resolveStateCode(customer).orElse(null);
        boolean sameState   = gstJurisdictionService.isIntraState(shopCode, customerCode);
        boolean isUT        = shopCode != null && gstJurisdictionService.isUnionTerritory(shopCode);

        // ── Pass 1: build SaleItems and collect raw (pre-invoice-discount) taxable values ─
        // The bill-level invoice discount is NOT subtracted here; per-line discounts are.
        // This preserves the raw taxable per line so the next step can allocate
        // the invoice discount proportionally (Section 15(3)(b)).
        record ItemBuildState(SaleItem item, BigDecimal rawTaxable, GSTType gstType) {}
        List<ItemBuildState> buildStates   = new ArrayList<>();
        List<BigDecimal>     rawTaxables   = new ArrayList<>();
        BigDecimal           totalRawTaxable = ZERO;

        for (SaleItemDto itemDto : dto.getItems()) {
            // Catalog line: item_variant_id present → look up variant + check stock.
            // Custom line (free-text service / one-off): item_variant_id null → skip stock, use DTO-provided GST rate.
            ItemVariant itemVariant = null;
            if (itemDto.getItemVariantId() != null) {
                itemVariant = itemVariantRepository.findById(itemDto.getItemVariantId())
                        .orElseThrow(() -> new EntityNotFoundAppException("Item Variant", itemDto.getItemVariantId()));

                if (!stockService.isStockAvailable(itemDto.getItemVariantId(), itemDto.getQty())) {
                    logger.warn("Insufficient stock for item: {}", itemDto.getItemName());
                    throw new InsufficientStockException("Insufficient stock for item: " + itemDto.getItemName());
                }
            }

            SaleItem saleItem = new SaleItem();
            saleItem.setItemVariant(itemVariant);
            if (itemVariant == null) {
                String customName = (itemDto.getCustomItemName() != null && !itemDto.getCustomItemName().isBlank())
                        ? itemDto.getCustomItemName()
                        : itemDto.getItemName();
                saleItem.setCustomItemName(customName);
                saleItem.setCustomDescription(itemDto.getCustomDescription());
                saleItem.setCustomHsnSac(itemDto.getCustomHsnSac());
                saleItem.setCustomUnit(itemDto.getCustomUnit());
            }
            saleItem.setQty(itemDto.getQty());
            saleItem.setUnitPrice(itemDto.getUnitPrice());
            saleItem.setDiscount(itemDto.getDiscount() != null ? itemDto.getDiscount() : ZERO);
            saleItem.setBatchNumber(itemDto.getBatchNumber());
            saleItem.setExpiryDate(itemDto.getExpiryDate());
            if (itemDto.getSalespersonId() != null) saleItem.setSalespersonId(itemDto.getSalespersonId());

            BigDecimal rawTaxable = com.desitech.vyaparsathi.gst.util.GstTaxCalculator.taxableValue(
                    itemDto.getQty(), itemDto.getUnitPrice(),
                    itemDto.getDiscount() != null ? itemDto.getDiscount() : ZERO);

            Integer effectiveGstRate = itemVariant != null
                    ? itemVariant.getGstRate()
                    : (itemDto.getGstRate() > 0 ? itemDto.getGstRate() : null);
            GSTType gstType = (gstApplicable && effectiveGstRate != null)
                    ? GSTType.fromRateOrDefault(effectiveGstRate) : GSTType.GST_0;
            saleItem.setGstType(gstType);

            buildStates.add(new ItemBuildState(saleItem, rawTaxable, gstType));
            rawTaxables.add(rawTaxable);
            totalRawTaxable = totalRawTaxable.add(rawTaxable);
            saleItems.add(saleItem);
        }

        // ── Section 15(3)(b): proportional bill-level discount allocation ──────────
        // Discount reduces each line's taxable value in proportion to its share of
        // totalRawTaxable, BEFORE GST is computed. FLOOR + last-line residual ensures
        // sum(allocated) == invoiceDisc exactly with no floating-point drift.
        List<BigDecimal> propDiscounts =
                com.desitech.vyaparsathi.gst.util.GstTaxCalculator.allocateDiscount(rawTaxables, invoiceDisc);

        // ── Pass 2: apply discount per line + compute GST via GstTaxCalculator ──────
        // GstTaxCalculator.computeLineGst() applies the FLOOR half-complement split for
        // CGST/SGST, eliminating the odd-paise error from the previous HALF_UP halving.
        for (int i = 0; i < buildStates.size(); i++) {
            ItemBuildState s = buildStates.get(i);
            BigDecimal reducedTaxable = s.rawTaxable().subtract(propDiscounts.get(i)).max(ZERO);
            s.item().setTaxableValue(reducedTaxable);
            totalTaxableValue = totalTaxableValue.add(reducedTaxable);

            if (gstApplicable && s.gstType().getRate().signum() > 0) {
                com.desitech.vyaparsathi.gst.util.GstTaxCalculator.LineGst lg =
                        com.desitech.vyaparsathi.gst.util.GstTaxCalculator.computeLineGst(
                                reducedTaxable, s.gstType(), s.item().getCessRate(), sameState, isUT);
                s.item().setCgstAmt(lg.cgst());
                s.item().setSgstAmt(lg.sgst());
                s.item().setUtgstAmt(lg.utgst());
                s.item().setIgstAmt(lg.igst());
                s.item().setCessAmt(lg.cess());
                totalGSTAmount = totalGSTAmount.add(lg.totalGst());
            } else {
                s.item().setCgstAmt(ZERO); s.item().setSgstAmt(ZERO);
                s.item().setIgstAmt(ZERO); s.item().setUtgstAmt(ZERO); s.item().setCessAmt(ZERO);
            }
        }

        // ── Composite supply charge GST (CA-7, Section 8(a) CGST Act) ────────────
        // Shipping and other charges are part of the composite supply and must be taxed
        // at the principal supply rate (highest rate among invoice lines).
        BigDecimal chargePrincipal = shippingCharges.add(otherCharges);
        BigDecimal compositeChargeGST = ZERO;
        com.desitech.vyaparsathi.gst.util.GstTaxCalculator.LineGst chargeLineGst = null;
        if (gstApplicable && chargePrincipal.compareTo(ZERO) > 0 && !buildStates.isEmpty()) {
            GSTType compositeRate = buildStates.stream()
                    .map(ItemBuildState::gstType)
                    .filter(t -> t != null && t.getRate().signum() > 0)
                    .max(java.util.Comparator.comparing(GSTType::getRate))
                    .orElse(GSTType.GST_0);
            if (compositeRate.getRate().signum() > 0) {
                chargeLineGst = com.desitech.vyaparsathi.gst.util.GstTaxCalculator.computeLineGst(
                        chargePrincipal, compositeRate, BigDecimal.ZERO, sameState, isUT);
                compositeChargeGST = chargeLineGst.totalGst();
            }
        }

        com.desitech.vyaparsathi.sales.enums.SaleType saleType = parsedType;
        boolean isProforma = saleType == com.desitech.vyaparsathi.sales.enums.SaleType.PROFORMA;

        // 3. Generate Invoice Number (atomic — no race condition).
        // Each sale type has a distinct number series so a real invoice sequence
        // is never "wasted" by a proforma or a bill of supply.
        LocalDate saleDate = dto.getDate() != null ? dto.getDate().toLocalDate() : LocalDate.now();
        String numberPrefix = numberPrefixFor(saleType, shop);
        String invoiceNo = invoiceNumberService.nextInvoiceNumber(shop.getId(), numberPrefix, saleDate);

        // 4. Calculate Final Totals
        // invoiceDisc is already baked into per-line taxable values via proportional
        // allocation — do NOT subtract it again here.
        // Grand total = reduced item totals + item GST + charge principal + charge GST.
        BigDecimal grandTotal = totalTaxableValue
                .add(totalGSTAmount)
                .add(chargePrincipal)
                .add(compositeChargeGST)
                .max(ZERO);
        BigDecimal finalTotalAmount = grandTotal.setScale(0, RoundingMode.HALF_UP);
        BigDecimal roundOff = finalTotalAmount.subtract(grandTotal);

        // 5. Credit-hold + credit-limit gate (V115 enterprise) — skip
        // for PROFORMA sales (non-binding, no receivable created) and
        // when there is no linked customer (walk-in / cash sale).
        if (!isProforma && customer != null) {
            enforceCreditGate(customer, finalTotalAmount);
        }

        // 6. Deduct Stock — skipped for PROFORMA sales (non-binding, no goods
        // have moved yet). Custom/service lines are always skipped (no inventory).
        if (!isProforma) {
            for (SaleItem item : saleItems) {
                if (item.getItemVariant() == null) continue;
                stockService.deductStock(item.getItemVariant().getId(), item.getQty(), "Sale Transaction", "Sale #" + invoiceNo);
            }
        }

        // 6. Persist Sale Entity
        Sale sale = new Sale();
        sale.setInvoiceNo(invoiceNo);
        sale.setShop(shop);
        sale.setCustomer(customer);
        sale.setTotalAmount(finalTotalAmount);
        sale.setRoundOff(roundOff);
        sale.setInvoiceDiscount(invoiceDisc);
        sale.setShippingCharges(shippingCharges);
        sale.setOtherCharges(otherCharges);
        // Composite supply charge GST breakdown (CA-7) — null-safe when no charges billed
        if (chargeLineGst != null) {
            sale.setCompositeChargeCgst(chargeLineGst.cgst());
            sale.setCompositeChargeSgst(chargeLineGst.sgst());
            sale.setCompositeChargeIgst(chargeLineGst.igst());
            sale.setCompositeChargeUtgst(chargeLineGst.utgst());
        }
        sale.setSyncedFlag(false);
        sale.setSaleItems(saleItems);
        saleItems.forEach(si -> si.setSale(sale));

        sale.setIsGstRequired(gstApplicable);
        sale.setReverseCharge(Boolean.TRUE.equals(dto.getReverseCharge()));
        // V99 statutory fields — freeze at doc creation time. PoS is nullable;
        // the SaleDocumentMapper falls back to customer/shop state when empty.
        sale.setPlaceOfSupply(dto.getPlaceOfSupply());
        sale.setSupplyType(SupplyType.fromString(dto.getSupplyType()));
        sale.setBillToPartySnapshot(dto.getBillToAddress());
        sale.setShipToPartySnapshot(dto.getShipToAddress());
        sale.setConsigneePartySnapshot(dto.getConsigneeAddress());
        sale.setSaleType(saleType);
        // Persist idempotency key + optional salesperson/notes.
        if (dto.getIdempotencyKey() != null && !dto.getIdempotencyKey().isBlank()) {
            sale.setIdempotencyKey(dto.getIdempotencyKey().trim());
        }
        if (dto.getSalespersonId() != null) sale.setSalespersonId(dto.getSalespersonId());
        if (dto.getNotes() != null) sale.setNotes(dto.getNotes());

        Sale savedSale = saleRepository.saveAndFlush(sale);

        // 7. Handle Delivery
        if (dto.getDelivery() != null) {
            DeliveryDTO deliveryDTO = dto.getDelivery();
            deliveryDTO.setSaleId(savedSale.getId());
            deliveryDTO.setInvoiceNumber(invoiceNo);
            if (customer != null) deliveryDTO.setCustomerName(customer.getName());
            deliveryService.createDelivery(deliveryDTO);
        }

        // 8. Ledger Entry & Advance Liquidation — skipped for PROFORMA.
        // A proforma does not create a receivable; the customer does not owe
        // money until a real invoice is issued via convertProformaToInvoice.
        if (customer != null && !isProforma) {
            // A. Record the initial Debt (CREDIT increases Customer's payable balance)
            CustomerLedgerDto saleLedgerDto = new CustomerLedgerDto();
            saleLedgerDto.setAmount(finalTotalAmount);
            saleLedgerDto.setType(CustomerLedgerType.CREDIT);
            saleLedgerDto.setDescription("Sale #" + invoiceNo);
            ledgerService.addEntry(customer.getId(), saleLedgerDto);

            // B. Apply existing Advance Pool (Drains available credits to pay this sale)
            BigDecimal advanceApplied = paymentService.applyAdvanceToSale(customer.getId(), savedSale.getId(), finalTotalAmount);

        }

        // 9. Process Fresh Payments (e.g., Cash paid at counter after advance was applied)
        if (dto.getPaymentDetails() != null && !dto.getPaymentDetails().isEmpty()) {
            // Calculate remaining gap after advance application
            BigDecimal remainingDue = paymentService.calculateDueAmount(savedSale.getId(), PaymentSourceType.SALE, finalTotalAmount);

            BigDecimal totalPaidInput = dto.getPaymentDetails().stream()
                    .map(p -> p.getAmount() != null ? p.getAmount() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            // Validation: Ensure fresh payment doesn't exceed the REMAINING balance
            if (totalPaidInput.compareTo(remainingDue) > 0) {
                throw new BusinessValidationException("Payment amount exceeds the remaining due after advance: ₹" + remainingDue);
            }

            for (PaymentDto paymentDTO : dto.getPaymentDetails()) {
                if (remainingDue.compareTo(BigDecimal.ZERO) <= 0) break;

                paymentDTO.setSourceId(savedSale.getId());
                paymentDTO.setSourceType(PaymentSourceType.SALE);
                paymentDTO.setCustomerId(customer != null ? customer.getId() : null);
                paymentDTO.setPaymentDate(LocalDateTime.now());

                // Creates Payment record + Ledger DEBIT automatically
                paymentService.createPayment(paymentDTO);

                remainingDue = remainingDue.subtract(paymentDTO.getAmount());
            }
        }

        // 10. ChangeLog, JWT Token Generation and Response
        changeLogService.append("SALE", savedSale.getId(), com.desitech.vyaparsathi.changelog.model.ChangeLogOperation.CREATE, mapper.toDto(savedSale), "LOCAL_DEVICE");

        String signedToken = jwtUtil.generateInvoiceToken(savedSale.getId(), sale.getInvoiceNo(), savedSale.getShop() != null ? savedSale.getShop().getId() : null);
        SaleDto resultDto = mapper.toDto(savedSale);
        resultDto.setSignedInvoiceUrl("/api/invoices/signed?token=" + signedToken);

        logger.info("Sale created successfully: ID={}, Invoice={}, Applied Advance=₹{}",
                savedSale.getId(), savedSale.getInvoiceNo(), (customer != null ? "Checked" : "N/A"));

        return resultDto;
    }

    @Transactional
    public void processSaleReturn(SaleReturnDto returnDto) {
        Sale sale = saleRepository.findById(returnDto.getSaleId())
                .orElseThrow(() -> new EntityNotFoundAppException("Sale", returnDto.getSaleId()));

        checkPeriodLock(sale.getDate() != null ? sale.getDate().toLocalDate() : LocalDate.now());

        // Only completed sales (or partially-returned rows getting further returns) may be returned.
        if (sale.getStatus() != SaleStatus.COMPLETED && sale.getStatus() != SaleStatus.PARTIALLY_RETURNED) {
            throw new BusinessValidationException(
                    "Cannot return items from a sale in state " + sale.getStatus() +
                    ". Only COMPLETED or PARTIALLY_RETURNED sales are returnable.");
        }

        BigDecimal totalReturnAmount = BigDecimal.ZERO;
        // Track (SaleItem → qty being returned on THIS pass) so we can hand the
        // aggregated set to CreditNoteService and produce a document with proper
        // line items after this loop completes.
        Map<SaleItem, BigDecimal> returnedThisPass = new LinkedHashMap<>();

        // Match by SaleItem PK — FE now sends the real saleItemId (mapped from
        // SaleItem.id, distinct from the itemVariantId JSON-aliased as `id`).
        for (SaleReturnDto.SaleReturnItemDto returnItem : returnDto.getReturnItems()) {
            Long lookupId = returnItem.getSaleItemId();
            SaleItem saleItem = sale.getSaleItems().stream()
                    .filter(si -> si.getId() != null && si.getId().equals(lookupId))
                    .findFirst()
                    .orElseThrow(() -> new EntityNotFoundAppException("Sale Item", lookupId));

            BigDecimal currentReturned = saleItem.getReturnedQty() != null ? saleItem.getReturnedQty() : BigDecimal.ZERO;
            BigDecimal requestedQty = returnItem.getReturnQuantity();
            BigDecimal originalQty  = saleItem.getQty();

            // Over-return guard: cumulative returned qty must never exceed originally sold qty.
            if (requestedQty == null || requestedQty.compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessValidationException("Return quantity must be positive for sale item " + saleItem.getId());
            }
            if (currentReturned.add(requestedQty).compareTo(originalQty) > 0) {
                throw new BusinessValidationException(
                        "Return quantity (" + requestedQty + ") exceeds remaining sold quantity ("
                                + originalQty.subtract(currentReturned) + ") for sale item " + saleItem.getId());
            }

            returnedThisPass.merge(saleItem, requestedQty, BigDecimal::add);

            // FIX (Phase 0.5): Return amount must be proportional to the full line amount
            // (taxableValue + GST), NOT just unitPrice × qty, which ignores discounts and tax.
            BigDecimal lineGst = (saleItem.getCgstAmt() != null ? saleItem.getCgstAmt() : ZERO)
                    .add(saleItem.getSgstAmt() != null ? saleItem.getSgstAmt() : ZERO)
                    .add(saleItem.getIgstAmt() != null ? saleItem.getIgstAmt() : ZERO);
            BigDecimal lineTaxableValue = saleItem.getTaxableValue() != null ? saleItem.getTaxableValue() : ZERO;
            BigDecimal fullLineAmount   = lineTaxableValue.add(lineGst);

            BigDecimal returnLineAmount;
            if (originalQty != null && originalQty.compareTo(ZERO) > 0) {
                // Proportional: returnAmt = (returnedQty / originalQty) * fullLineAmount
                returnLineAmount = fullLineAmount
                        .multiply(requestedQty)
                        .divide(originalQty, 2, RoundingMode.HALF_UP);
            } else {
                returnLineAmount = ZERO;
            }

            totalReturnAmount = totalReturnAmount.add(returnLineAmount);
            saleItem.setReturnedQty(currentReturned.add(requestedQty));
            saleItem.setReturned(true);

            if (saleItem.getItemVariant() != null) {
                StockAdjustmentDto adjustment = new StockAdjustmentDto();
                adjustment.setItemVariantId(saleItem.getItemVariant().getId());
                adjustment.setAdjustmentQuantity(requestedQty);
                adjustment.setReason("Return: Inv #" + sale.getInvoiceNo());
                stockService.adjustStock(adjustment);
            }
            // Custom/service lines have no inventory to restore — return-amount only.
        }

        // 2. Calculate the Debt vs. Cash situation
        BigDecimal totalOriginalAmount = sale.getTotalAmount();
        BigDecimal paidBeforeReturn = paymentService.getTotalPaidBySaleIds(Set.of(sale.getId())).getOrDefault(sale.getId(), BigDecimal.ZERO);
        BigDecimal unpaidDebtBeforeReturn = totalOriginalAmount.subtract(paidBeforeReturn).max(BigDecimal.ZERO);

        // 3. Update Sale Header — distinguish full return from partial return
        // so the audit trail reflects the actual state.
        sale.setTotalAmount(totalOriginalAmount.subtract(totalReturnAmount).max(BigDecimal.ZERO));
        boolean allFullyReturned = sale.getSaleItems().stream()
                .allMatch(item -> {
                    BigDecimal r = item.getReturnedQty() != null ? item.getReturnedQty() : BigDecimal.ZERO;
                    return r.compareTo(item.getQty()) >= 0;
                });
        boolean anyReturned = sale.getSaleItems().stream()
                .anyMatch(item -> {
                    BigDecimal r = item.getReturnedQty() != null ? item.getReturnedQty() : BigDecimal.ZERO;
                    return r.compareTo(BigDecimal.ZERO) > 0;
                });
        if (allFullyReturned) {
            sale.setStatus(SaleStatus.RETURNED);
        } else if (anyReturned) {
            sale.setStatus(SaleStatus.PARTIALLY_RETURNED);
        }
        saleRepository.save(sale);

        // 4. Financial Adjustments (The Fix is Here)
        if (sale.getCustomer() != null && totalReturnAmount.compareTo(BigDecimal.ZERO) > 0) {

            // A. Handle the Unpaid Portion (Credit Note)
            // This clears the debt the customer NEVER paid.
            BigDecimal debtToCancel = totalReturnAmount.min(unpaidDebtBeforeReturn);
            if (debtToCancel.compareTo(BigDecimal.ZERO) > 0) {
                CustomerLedgerDto creditNote = new CustomerLedgerDto();
                creditNote.setAmount(debtToCancel);
                creditNote.setType(CustomerLedgerType.DEBIT);
                creditNote.setDescription("Sales Return (Debt Cancel) - Inv #" + sale.getInvoiceNo());
                ledgerService.addEntry(sale.getCustomer().getId(), creditNote);
            }

            // B. Handle the Paid Portion (Refund/Advance)
            // This moves REAL CASH to the customer's advance balance.
            if (returnDto.isRefundPayment()) {
                BigDecimal cashToMoveToAdvance = totalReturnAmount.subtract(debtToCancel).max(BigDecimal.ZERO);

                if (cashToMoveToAdvance.compareTo(BigDecimal.ZERO) > 0) {
                    BulkPaymentRequest bulkRequest = new BulkPaymentRequest();
                    bulkRequest.setCustomerId(sale.getCustomer().getId());
                    bulkRequest.setTotalAmount(cashToMoveToAdvance);
                    bulkRequest.setPaymentMethod(PaymentMethod.OTHER);
                    bulkRequest.setPaymentDate(LocalDateTime.now());
                    bulkRequest.setReference("Return Refund to Advance - Inv #" + sale.getInvoiceNo());
                    bulkRequest.setSelectedSaleIds(new ArrayList<>());

                    paymentService.bulkPayment(bulkRequest);
                }
            }
        }
        // 5. Atomically issue the formal Credit Note document in the same
        // transaction as the ledger adjustment. If it throws, the whole
        // return rolls back — no more silent credit-note failures leaving
        // the ledger adjusted but the document missing.
        if (!returnedThisPass.isEmpty() && totalReturnAmount.compareTo(BigDecimal.ZERO) > 0) {
            CreditNote issued = creditNoteService.createFromSaleReturn(sale, returnedThisPass,
                    "Sales Return - Inv #" + sale.getInvoiceNo());
            logger.info("Issued CreditNote {} for saleId={} total={}",
                    issued.getCreditNoteNo(), sale.getId(), issued.getTotalAmount());
        }

        auditHelper.log("PROCESS_RETURN", "SALE", sale.getId().toString(), "Returned: " + totalReturnAmount);
        changeLogService.append("SALE_RETURN", sale.getId(),
                com.desitech.vyaparsathi.changelog.model.ChangeLogOperation.RETURN, returnDto, "LOCAL_DEVICE");
    }

    @Transactional
    public void cancelSale(Long saleId, String reason) {
        BigDecimal totalPaid = ZERO;
        Sale sale = saleRepository.findById(saleId)
                .orElseThrow(() -> new EntityNotFoundAppException("Sale", saleId));
        checkPeriodLock(sale.getDate() != null ? sale.getDate().toLocalDate() : LocalDate.now());

        // Idempotency / correctness: cancelling an already-terminal sale must be a
        // no-op-or-error, never re-restock inventory and re-post the ledger reversal.
        if (sale.getStatus() == SaleStatus.CANCELLED) {
            throw new BusinessValidationException("Sale " + sale.getInvoiceNo() + " is already cancelled.");
        }
        if (sale.getStatus() == SaleStatus.RETURNED) {
            throw new BusinessValidationException(
                    "Sale " + sale.getInvoiceNo() + " has been fully returned — cancel is not applicable.");
        }
        if (sale.getStatus() == SaleStatus.DRAFT) {
            throw new BusinessValidationException(
                    "Draft sale " + sale.getInvoiceNo() + " has no ledger or stock impact — delete the draft instead.");
        }

        // 1. Return Stock to Inventory (skip custom/service lines — no inventory to restore)
        for (SaleItem saleItem : sale.getSaleItems()) {
            if (saleItem.getItemVariant() == null) continue;
            StockAdjustmentDto adjustment = new StockAdjustmentDto();
            adjustment.setItemVariantId(saleItem.getItemVariant().getId());
            adjustment.setAdjustmentQuantity(saleItem.getQty());
            adjustment.setReason("Cancelled Sale #" + sale.getInvoiceNo());
            stockService.adjustStock(adjustment);
        }

        if (sale.getCustomer() != null) {
            // 2. Reverse the Sale Debt (DEBIT)
            CustomerLedgerDto reverseDto = new CustomerLedgerDto();
            reverseDto.setAmount(sale.getTotalAmount());
            reverseDto.setType(CustomerLedgerType.DEBIT);
            reverseDto.setDescription("Cancelled Sale #" + sale.getInvoiceNo() +
                    (reason != null ? " - " + reason : ""));
            ledgerService.addEntry(sale.getCustomer().getId(), reverseDto);

            // 3. Handle existing payments
            totalPaid = paymentService.getTotalPaidBySaleIds(Set.of(sale.getId()))
                    .getOrDefault(sale.getId(), ZERO);

            if (totalPaid.compareTo(ZERO) > 0) {
            /* Instead of just a ledger entry, we use your bulkPayment logic
               with an empty list to convert these "lost" payments into
               an Advance/Credit for the customer.
            */
                BulkPaymentRequest refundToAdvance = new BulkPaymentRequest();
                refundToAdvance.setCustomerId(sale.getCustomer().getId());
                refundToAdvance.setTotalAmount(totalPaid);
                refundToAdvance.setPaymentMethod(PaymentMethod.OTHER);
                refundToAdvance.setPaymentDate(LocalDateTime.now());
                refundToAdvance.setReference("CANCEL-REFUND-" + sale.getInvoiceNo());
                refundToAdvance.setSelectedSaleIds(new ArrayList<>()); // This triggers advance logic

                paymentService.bulkPayment(refundToAdvance);

                // Note: We don't need a manual CREDIT ledger entry here because
                // bulkPayment's ledger logic will handle it.
            }
        }

        // 4. Finalize Sale State
        sale.setTotalAmount(ZERO);
        sale.setStatus(SaleStatus.CANCELLED);
        saleRepository.save(sale);

        logger.info("Cancelled sale with ID {} and reversed {} in payments", saleId, totalPaid);

        auditHelper.log(
                "CANCEL_SALE",
                "SALE",
                saleId.toString(),
                "Reason: " + reason
        );
        changeLogService.append("SALE_CANCEL", sale.getId(),
                com.desitech.vyaparsathi.changelog.model.ChangeLogOperation.CANCEL,
                java.util.Map.of("reason", reason != null ? reason : "No reason provided"), "LOCAL_DEVICE");
    }

    /**
     * Discard a DRAFT (or HELD) sale outright — used when the caller has moved on
     * to a different sale-type (e.g. proforma) and the draft/held record would
     * otherwise be orphaned. Rejects any other status (COMPLETED etc. must use
     * cancelSale instead — they have ledger/stock impact).
     */
    @Transactional
    public void discardDraft(Long saleId) {
        Sale sale = saleRepository.findById(saleId)
                .orElseThrow(() -> new EntityNotFoundAppException("Sale", saleId));
        if (sale.getStatus() != SaleStatus.DRAFT && sale.getStatus() != SaleStatus.HELD) {
            throw new BusinessValidationException(
                    "Only DRAFT or HELD sales can be discarded — sale " + sale.getInvoiceNo()
                            + " is in state " + sale.getStatus() + ". Use cancel for committed sales.");
        }
        auditHelper.log("DISCARD_DRAFT", "SALE", saleId.toString(),
                "invoiceNo=" + sale.getInvoiceNo() + " status=" + sale.getStatus());
        saleRepository.delete(sale);
    }

    /**
     * Park a DRAFT sale — user explicitly parks a work-in-progress cart to serve
     * the next customer. Same underlying state as DRAFT (no ledger, no stock) but
     * the HELD label lets the UI list "parked" separately from "auto-saved draft"
     * and offer a Resume action. Idempotent: parking a HELD sale is a no-op.
     */
    @Transactional
    public SaleDto parkSale(Long saleId) {
        Sale sale = saleRepository.findById(saleId)
                .orElseThrow(() -> new EntityNotFoundAppException("Sale", saleId));
        if (sale.getStatus() == SaleStatus.HELD) {
            return mapper.toDto(sale);  // idempotent
        }
        if (sale.getStatus() != SaleStatus.DRAFT) {
            throw new BusinessValidationException(
                    "Only DRAFT sales can be parked — sale " + sale.getInvoiceNo()
                            + " is in state " + sale.getStatus());
        }
        sale.setStatus(SaleStatus.HELD);
        Sale saved = saleRepository.save(sale);
        auditHelper.log("PARK_SALE", "SALE", saleId.toString(), null);
        return mapper.toDto(saved);
    }

    /**
     * Resume a HELD sale back to DRAFT so the standard complete-draft flow works
     * unchanged. Idempotent for DRAFT.
     */
    @Transactional
    public SaleDto resumeSale(Long saleId) {
        Sale sale = saleRepository.findById(saleId)
                .orElseThrow(() -> new EntityNotFoundAppException("Sale", saleId));
        if (sale.getStatus() == SaleStatus.DRAFT) {
            return mapper.toDto(sale);  // idempotent
        }
        if (sale.getStatus() != SaleStatus.HELD) {
            throw new BusinessValidationException(
                    "Only HELD sales can be resumed — sale " + sale.getInvoiceNo()
                            + " is in state " + sale.getStatus());
        }
        sale.setStatus(SaleStatus.DRAFT);
        Sale saved = saleRepository.save(sale);
        auditHelper.log("RESUME_SALE", "SALE", saleId.toString(), null);
        return mapper.toDto(saved);
    }

    /**
     * Update sale-level notes only. Non-destructive: accepts null to explicitly
     * clear notes ({@code PATCH} semantics). Any sale in any status may have its
     * notes edited — notes are metadata and never affect ledger or stock.
     */
    @Transactional
    public SaleDto updateNotes(Long saleId, String notes) {
        Sale sale = saleRepository.findById(saleId)
                .orElseThrow(() -> new EntityNotFoundAppException("Sale", saleId));
        sale.setNotes(notes);
        Sale saved = saleRepository.save(sale);
        auditHelper.log("UPDATE_SALE_NOTES", "SALE", saleId.toString(),
                notes == null ? "cleared" : ("len=" + notes.length()));
        return mapper.toDto(saved);
    }

    /**
     * Timeline of state-mutating events for a single sale — used by the FE
     * "Void / refund history" dialog. Merges two sources:
     *   • AuditLog rows filtered by (entity=SALE, entityId=saleId) — narrated
     *     via {@code auditHelper.log()} on every mutation.
     *   • Credit notes linked to the sale — carry the money impact of returns
     *     that AuditLog alone can't render (CN number, amount).
     * <p>Rows are returned newest-first. Both source queries are auto-scoped
     * to the current shop by {@code ShopFilterAspect}, so no explicit shopId.
     */
    public java.util.List<com.desitech.vyaparsathi.sales.dto.SaleTimelineEventDto> getSaleTimeline(Long saleId) {
        // Existence + shop-scope guard: findById is aspect-filtered, so a foreign
        // shop's saleId looks unresolvable rather than leaking data.
        Sale sale = saleRepository.findById(saleId)
                .orElseThrow(() -> new EntityNotFoundAppException("Sale", saleId));

        java.util.List<com.desitech.vyaparsathi.sales.dto.SaleTimelineEventDto> events = new java.util.ArrayList<>();

        // Audit rows.
        java.util.List<com.desitech.vyaparsathi.audit.entity.AuditLog> auditRows =
                auditLogRepository.findByEntityAndEntityIdOrderByTimestampDesc("SALE", saleId.toString());
        for (com.desitech.vyaparsathi.audit.entity.AuditLog row : auditRows) {
            events.add(new com.desitech.vyaparsathi.sales.dto.SaleTimelineEventDto(
                    "audit-" + row.getId(),
                    row.getAction(),
                    row.getDetails(),
                    row.getUsername(),
                    row.getTimestamp(),
                    null,
                    null));
        }

        // Credit notes for this sale — anchor at start of day so ordering with
        // the timestamped audit rows is stable.
        java.util.List<CreditNote> notes = creditNoteRepository.findBySaleIdOrderByIdDesc(saleId);
        for (CreditNote cn : notes) {
            LocalDateTime cnStamp = cn.getCreditNoteDate() == null
                    ? sale.getDate()
                    : cn.getCreditNoteDate().atStartOfDay();
            events.add(new com.desitech.vyaparsathi.sales.dto.SaleTimelineEventDto(
                    "cn-" + cn.getId(),
                    "CREDIT_NOTE",
                    "Credit note " + cn.getCreditNoteNo() + (cn.getReason() == null ? "" : " — " + cn.getReason()),
                    null,
                    cnStamp,
                    cn.getCreditNoteNo(),
                    cn.getTotalAmount()));
        }

        // Newest first — nulls at the bottom.
        events.sort((a, b) -> {
            LocalDateTime ta = a.getTimestamp();
            LocalDateTime tb = b.getTimestamp();
            if (ta == null && tb == null) return 0;
            if (ta == null) return 1;
            if (tb == null) return -1;
            return tb.compareTo(ta);
        });
        return events;
    }

    public Optional<SaleDto> getSaleById(Long saleId) {
        return saleRepository.findById(saleId)
                .map(sale -> {
                    SaleDto dto = mapper.toDto(sale);

                    // Populate History for each item
                    if (dto.getItems() != null) {
                        for (SaleItemDto itemDto : dto.getItems()) {
                            if (itemDto.getItemVariantId() == null) continue;
                            sale.getSaleItems().stream()
                                    .filter(si -> si.getItemVariant() != null
                                            && si.getItemVariant().getId().equals(itemDto.getItemVariantId()))
                                    .findFirst()
                                    .ifPresent(si -> {
                                        BigDecimal purchased = si.getQty() != null ? si.getQty() : BigDecimal.ZERO;
                                        BigDecimal returned = si.getReturnedQty() != null ? si.getReturnedQty() : BigDecimal.ZERO;

                                        // Set both history fields in the DTO
                                        itemDto.setReturnedQty(returned);
                                        itemDto.setNetQty(purchased.subtract(returned));
                                    });
                        }
                    }

                    // Standard Delivery & Payment logic
                    deliveryRepository.findBySaleIdOrderByCreatedAtDesc(saleId)
                            .stream().findFirst()
                            .ifPresent(d -> dto.setDelivery(deliveryMapper.toDto(d)));

                    Map<Long, BigDecimal> paidMap = paymentService.getTotalPaidBySaleIds(Set.of(saleId));
                    BigDecimal paid = paidMap.getOrDefault(saleId, ZERO);

                    dto.setPaidAmount(paid);
                    // Important: totalAmount was already reduced in processSaleReturn
                    dto.setDueAmount(sale.getTotalAmount().subtract(paid));

                    return dto;
                });
    }
    public List<SaleDto> listSales(LocalDateTime startDate, LocalDateTime endDate) {
        if (startDate == null) {
            startDate = LocalDateTime.of(1970, 1, 1, 0, 0);
        }
        if (endDate == null) {
            endDate = LocalDateTime.now();
        } else {
            endDate = endDate.with(LocalTime.MAX);
        }

        List<Sale> sales = saleRepository.findAllByShopIdAndDateBetween(
                TenantContext.getCurrentShopId(), startDate, endDate);

        // Bulk fetch all payments for these sales
        Set<Long> saleIds = sales.stream()
                .filter(s -> s.getId() != null)
                .map(Sale::getId)
                .collect(Collectors.toSet());

        Map<Long, BigDecimal> paidBySale = paymentService.getTotalPaidBySaleIds(saleIds);

        List<SaleDto> saleDtos = new ArrayList<>();
        for (Sale sale : sales) {
            SaleDto dto = mapper.toDto(sale);
            BigDecimal totalAmount = sale.getTotalAmount() != null ? sale.getTotalAmount() : ZERO;
            BigDecimal paidAmount = paidBySale.getOrDefault(sale.getId(), ZERO);
            BigDecimal dueAmount = totalAmount.subtract(paidAmount);
            dto.setPaidAmount(paidAmount);
            dto.setDueAmount(dueAmount);
            saleDtos.add(dto);
        }

        logger.info("Fetched {} sales between {} and {}", sales.size(), startDate, endDate);
        return saleDtos;
    }

    public List<SaleDueDto> getSalesWithDue() {
        List<Sale> sales = saleRepository.findByStatus(SaleStatus.COMPLETED);

        Set<Long> saleIds = sales.stream().map(Sale::getId).collect(Collectors.toSet());
        Map<Long, BigDecimal> paidBySale = paymentService.getTotalPaidBySaleIds(saleIds);
        String shopName = currentShopName();

        return sales.stream()
                .map(sale -> mapToDueDto(sale, paidBySale.getOrDefault(sale.getId(), ZERO), shopName))
                .filter(dto -> dto.getDueAmount().compareTo(ZERO) > 0)
                .collect(Collectors.toList());
    }

    public Page<SaleDueDto> getSalesHistory(Pageable pageable) {
        return getSalesHistory(null, null, null, null, null, pageable);
    }

    /**
     * Filtered history — every param is optional (null = no filter). Backward-compatible
     * with the older no-arg {@link #getSalesHistory(Pageable)} which delegates here.
     */
    public Page<SaleDueDto> getSalesHistory(
            String q,
            SaleStatus status,
            Long customerId,
            LocalDateTime from,
            LocalDateTime to,
            Pageable pageable
    ) {
        Long shopId = TenantContext.getCurrentShopId();
        String normalizedQ = (q == null || q.isBlank()) ? null : q.trim();
        Page<Sale> salesPage = saleRepository.searchHistory(
                shopId, normalizedQ, status, customerId, from, to, pageable);

        Set<Long> saleIds = salesPage.getContent().stream()
                .map(Sale::getId).collect(Collectors.toSet());
        Map<Long, BigDecimal> paidBySale = paymentService.getTotalPaidBySaleIds(saleIds);
        String shopName = currentShopName();

        List<SaleDueDto> dtos = salesPage.getContent().stream()
                .map(sale -> mapToDueDto(sale, paidBySale.getOrDefault(sale.getId(), ZERO), shopName))
                .collect(Collectors.toList());

        return new PageImpl<>(dtos, pageable, salesPage.getTotalElements());
    }
    public SaleDueDto getSaleDueBySaleId(Long saleId) {
        Sale sale = saleRepository.findById(saleId)
                .orElseThrow(() -> new EntityNotFoundAppException("Sale", saleId));

        Map<Long, BigDecimal> paidMap = paymentService.getTotalPaidBySaleIds(Set.of(saleId));
        BigDecimal paid = paidMap.getOrDefault(saleId, ZERO);
        BigDecimal due = sale.getTotalAmount().subtract(paid);

        SaleDueDto dto = new SaleDueDto();
        dto.setSaleId(sale.getId());
        dto.setInvoiceNo(sale.getInvoiceNo());
        dto.setDueAmount(due);
        return dto;
    }

    public Page<SaleDueDto> getDuesByCustomerId(Long customerId, Pageable pageable) {
        Page<Sale> salesPage = saleRepository.findByCustomerIdAndStatus(customerId, SaleStatus.COMPLETED, pageable);

        Set<Long> saleIds = salesPage.getContent().stream().map(Sale::getId).collect(Collectors.toSet());
        Map<Long, BigDecimal> paidBySale = paymentService.getTotalPaidBySaleIds(saleIds);
        String shopName = currentShopName();

        List<SaleDueDto> dtos = salesPage.getContent().stream()
                .map(sale -> mapToDueDto(sale, paidBySale.getOrDefault(sale.getId(), ZERO), shopName))
                .collect(Collectors.toList());

        return new PageImpl<>(dtos, pageable, salesPage.getTotalElements());
    }

    /** One shop lookup per request — hoisted out of the per-row map loop. */
    private String currentShopName() {
        Long shopId = TenantContext.getCurrentShopId();
        if (shopId == null) return "";
        return shopRepository.findById(shopId).map(Shop::getName).orElse("");
    }
    @Transactional
    public SaleDto saveOrUpdateDraft(SaleDto dto) {
        Shop shop = shopRepository.findById(TenantContext.getCurrentShopId())
                .orElseThrow(() -> new EntityNotFoundAppException("Shop", TenantContext.getCurrentShopId()));

        Customer customer = (dto.getCustomer() != null) ?
                customerRepository.findById(dto.getCustomer().getId()).orElse(null) : null;

        Sale sale;
        boolean isUpdate = dto.getId() != null;

        if (isUpdate) {
            sale = saleRepository.findById(dto.getId())
                    .orElseThrow(() -> new EntityNotFoundAppException("Sale", dto.getId()));

            if (sale.getStatus() != SaleStatus.DRAFT) {
                throw new BusinessValidationException("Only drafts can be updated via this endpoint.");
            }
            // Clear items safely
            sale.getSaleItems().clear();
        } else {
            sale = new Sale();
            // Drafts get a distinct, short DRF sequence — separate from real invoices.
            // This keeps the real invoice sequence gap-free (an abandoned draft
            // does not "waste" a real invoice number, which matters for Indian GST
            // audit trails that require unbroken sequential numbering).
            sale.setInvoiceNo(invoiceNumberService.nextInvoiceNumber(shop.getId(), "DRF", LocalDate.now()));
            sale.setStatus(SaleStatus.DRAFT);
        }

        BigDecimal totalTaxableValue = ZERO;
        List<SaleItem> itemsToUpdate = new ArrayList<>();

        for (SaleItemDto itemDto : dto.getItems()) {
            ItemVariant iv = null;
            if (itemDto.getItemVariantId() != null) {
                iv = itemVariantRepository.findById(itemDto.getItemVariantId())
                        .orElseThrow(() -> new EntityNotFoundAppException("Item Variant", itemDto.getItemVariantId()));
            }

            SaleItem saleItem = new SaleItem();
            saleItem.setItemVariant(iv);
            if (iv == null) {
                String customName = (itemDto.getCustomItemName() != null && !itemDto.getCustomItemName().isBlank())
                        ? itemDto.getCustomItemName()
                        : itemDto.getItemName();
                saleItem.setCustomItemName(customName);
                saleItem.setCustomDescription(itemDto.getCustomDescription());
                saleItem.setCustomHsnSac(itemDto.getCustomHsnSac());
                saleItem.setCustomUnit(itemDto.getCustomUnit());
            }
            saleItem.setQty(itemDto.getQty());
            saleItem.setUnitPrice(itemDto.getUnitPrice());
            saleItem.setDiscount(itemDto.getDiscount() != null ? itemDto.getDiscount() : ZERO);

            BigDecimal taxableValue = itemDto.getQty().multiply(itemDto.getUnitPrice())
                    .subtract(saleItem.getDiscount());

            saleItem.setTaxableValue(taxableValue);
            totalTaxableValue = totalTaxableValue.add(taxableValue);

            // GST amounts are zero on drafts (final split is computed at completeDraft,
            // where the shop-vs-customer state comparison decides CGST+SGST vs IGST),
            // BUT the intended rate must still be persisted so:
            //   - custom/service line items (no ItemVariant to re-derive from) keep their rate,
            //   - the frontend can show "GST @ 18%" when the user reopens the draft.
            Integer effectiveGstRate = iv != null
                    ? iv.getGstRate()
                    : (itemDto.getGstRate() > 0 ? itemDto.getGstRate() : null);
            if (effectiveGstRate != null && effectiveGstRate > 0) {
                saleItem.setGstType(GSTType.fromRateOrDefault(effectiveGstRate));
            } else {
                saleItem.setGstType(GSTType.GST_0);
            }
            saleItem.setCgstAmt(ZERO);
            saleItem.setSgstAmt(ZERO);
            saleItem.setIgstAmt(ZERO);

            saleItem.setSale(sale);
            itemsToUpdate.add(saleItem);
        }

        sale.setShop(shop);
        sale.setCustomer(customer);
        // Preserve the "apply GST" intent on the draft — completeDraft re-reads
        // this flag from the completion DTO (line ~889), but the DTO's default
        // comes from what the frontend fetches back. Without persistence, the
        // flag silently drops to false and drafts complete with zero GST.
        // GST applicability is determined by the SHOP's registration status,
        // not the customer's GSTIN — the customer's GSTIN only affects ITC eligibility.
        sale.setIsGstRequired(Boolean.TRUE.equals(dto.getIsGstRequired()));
        sale.setReverseCharge(Boolean.TRUE.equals(dto.getReverseCharge()));
        // Allow updates to statutory fields on the update path too. Null-safe —
        // callers that don't touch them (POS retry, minor edits) skip via getter.
        if (dto.getPlaceOfSupply()   != null) sale.setPlaceOfSupply(dto.getPlaceOfSupply());
        if (dto.getSupplyType()      != null) sale.setSupplyType(SupplyType.fromString(dto.getSupplyType()));
        if (dto.getBillToAddress()   != null) sale.setBillToPartySnapshot(dto.getBillToAddress());
        if (dto.getShipToAddress()   != null) sale.setShipToPartySnapshot(dto.getShipToAddress());
        if (dto.getConsigneeAddress()!= null) sale.setConsigneePartySnapshot(dto.getConsigneeAddress());
        sale.setTotalAmount(totalTaxableValue.setScale(0, RoundingMode.HALF_UP));
        sale.setRoundOff(sale.getTotalAmount().subtract(totalTaxableValue)); // Difference for ledger balancing
        sale.getSaleItems().addAll(itemsToUpdate);

        Sale saved = saleRepository.save(sale);

        // Handle Delivery (Now uses saved.getInvoiceNo() to avoid null issues)
        if (dto.getDelivery() != null) {
            DeliveryDTO deliveryDTO = dto.getDelivery();
            deliveryDTO.setSaleId(saved.getId());
            deliveryDTO.setInvoiceNumber(saved.getInvoiceNo());
            if (customer != null) {
                deliveryDTO.setCustomerName(customer.getName());
            }
            deliveryService.createDelivery(deliveryDTO);
        }

        logger.info("Draft {} saved successfully", saved.getInvoiceNo());
        return mapper.toDto(saved);
    }
    @Transactional
    @LogAudit(action = "COMPLETE_SALE", entity = "SALE")
    @CheckSubscriptionLimit("SALES")
    public SaleDto completeDraft(SaleDto dto) {
        subscriptionService.validateSaleProcessingEntitlement(TenantContext.getCurrentShopId());
        checkPeriodLock(LocalDate.now());

        if (dto.getId() == null) {
            throw new BusinessValidationException("Sale ID is required to complete a draft");
        }

        Sale existing = saleRepository.findById(dto.getId())
                .orElseThrow(() -> new EntityNotFoundAppException("Sale", dto.getId()));

        if (existing.getStatus() != SaleStatus.DRAFT) {
            throw new BusinessValidationException("Only DRAFT sales can be completed");
        }

        Shop existingShop = existing.getShop();

        // Sale-type resolution FIRST — composition-scheme shops force BILL_OF_SUPPLY,
        // which needs a different number-series prefix ("BOS") than a real invoice.
        com.desitech.vyaparsathi.sales.enums.SaleType parsedType =
                effectiveSaleType(dto.getSaleType(), existingShop);
        boolean isBillOfSupply = parsedType == com.desitech.vyaparsathi.sales.enums.SaleType.BILL_OF_SUPPLY;
        boolean gstApplicable  = Boolean.TRUE.equals(dto.getIsGstRequired()) && !isBillOfSupply;
        if (gstApplicable && parsedType == com.desitech.vyaparsathi.sales.enums.SaleType.INVOICE) {
            validateHsnPresent(dto);
        }

        // Reissue the invoice number from the real (non-DRF) sequence. Drafts
        // carry a throwaway "DRF/YY-YY/NNNNN" number that must not appear on a
        // completed invoice — that would be non-compliant with GST audit trails
        // and confusing to customers. The old DRF number is discarded here.
        LocalDate completionDate = LocalDate.now();
        String finalInvoiceNo = invoiceNumberService.nextInvoiceNumber(
                existingShop.getId(), numberPrefixFor(parsedType, existingShop), completionDate);
        String previousDraftInvoiceNo = existing.getInvoiceNo();
        existing.setInvoiceNo(finalInvoiceNo);
        existing.setSaleType(parsedType);
        logger.info("Draft {} completed → issued invoice number {}", previousDraftInvoiceNo, finalInvoiceNo);

        Customer customer = Optional.ofNullable(dto.getCustomer())
                .map(c -> customerRepository.findById(c.getId())
                        .orElseThrow(() -> new EntityNotFoundAppException("Customer", c.getId())))
                .orElse(null);

        // Clear old draft items to replace with final ones
        existing.getSaleItems().clear();

        BigDecimal totalTaxableValue = ZERO;
        BigDecimal totalGSTAmount = ZERO;

        // Jurisdiction resolved once — no per-item work.
        String shopStateCode = gstJurisdictionService.resolveStateCode(existingShop).orElse(null);
        String customerStateCode = gstJurisdictionService.resolveStateCode(customer).orElse(null);
        boolean sameState = gstJurisdictionService.isIntraState(shopStateCode, customerStateCode);
        boolean utRegime = gstJurisdictionService.getIntraTaxRegime(shopStateCode)
                == com.desitech.vyaparsathi.gst.service.GstJurisdictionService.IntraTaxRegime.CGST_UTGST;

        for (SaleItemDto itemDto : dto.getItems()) {
            ItemVariant itemVariant = null;
            if (itemDto.getItemVariantId() != null) {
                itemVariant = itemVariantRepository.findById(itemDto.getItemVariantId())
                        .orElseThrow(() -> new EntityNotFoundAppException("Item Variant", itemDto.getItemVariantId()));

                if (!stockService.isStockAvailable(itemDto.getItemVariantId(), itemDto.getQty())) {
                    throw new InsufficientStockException("Insufficient stock for item: " + itemDto.getItemName());
                }
            }

            SaleItem saleItem = new SaleItem();
            saleItem.setSale(existing);
            saleItem.setItemVariant(itemVariant);
            if (itemVariant == null) {
                String customName = (itemDto.getCustomItemName() != null && !itemDto.getCustomItemName().isBlank())
                        ? itemDto.getCustomItemName()
                        : itemDto.getItemName();
                saleItem.setCustomItemName(customName);
                saleItem.setCustomDescription(itemDto.getCustomDescription());
                saleItem.setCustomHsnSac(itemDto.getCustomHsnSac());
                saleItem.setCustomUnit(itemDto.getCustomUnit());
            }
            saleItem.setQty(itemDto.getQty());
            saleItem.setUnitPrice(itemDto.getUnitPrice());
            saleItem.setDiscount(itemDto.getDiscount() != null ? itemDto.getDiscount() : ZERO);
            // Optional batch/expiry — used by FMCG / food / any perishable inventory.
            saleItem.setBatchNumber(itemDto.getBatchNumber());
            saleItem.setExpiryDate(itemDto.getExpiryDate());
            // Optional per-line salesperson attribution (V71 column).
            if (itemDto.getSalespersonId() != null) saleItem.setSalespersonId(itemDto.getSalespersonId());

            BigDecimal taxableValue = itemDto.getQty()
                    .multiply(itemDto.getUnitPrice())
                    .subtract(saleItem.getDiscount());

            saleItem.setTaxableValue(taxableValue);
            totalTaxableValue = totalTaxableValue.add(taxableValue);

            Integer effectiveGstRate = itemVariant != null
                    ? itemVariant.getGstRate()
                    : (itemDto.getGstRate() > 0 ? itemDto.getGstRate() : null);

            if (gstApplicable && effectiveGstRate != null) {
                GSTType gstType = GSTType.fromRateOrDefault(effectiveGstRate);
                BigDecimal gstAmount = taxableValue
                        .multiply(gstType.getRate())
                        .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

                saleItem.setGstType(gstType);

                if (sameState) {
                    BigDecimal half = gstAmount.divide(BigDecimal.valueOf(2), RoundingMode.HALF_UP);
                    saleItem.setCgstAmt(half);
                    if (utRegime) {
                        saleItem.setUtgstAmt(half);
                        saleItem.setSgstAmt(ZERO);
                    } else {
                        saleItem.setSgstAmt(half);
                        saleItem.setUtgstAmt(ZERO);
                    }
                    saleItem.setIgstAmt(ZERO);
                } else {
                    saleItem.setIgstAmt(gstAmount);
                    saleItem.setCgstAmt(ZERO);
                    saleItem.setSgstAmt(ZERO);
                    saleItem.setUtgstAmt(ZERO);
                }
                totalGSTAmount = totalGSTAmount.add(gstAmount);
            } else {
                saleItem.setGstType(GSTType.GST_0);
                saleItem.setCgstAmt(ZERO);
                saleItem.setSgstAmt(ZERO);
                saleItem.setIgstAmt(ZERO);
                saleItem.setUtgstAmt(ZERO);
            }
            existing.getSaleItems().add(saleItem);
        }

        // Deduct stock (only for catalog line items — custom/service lines have no inventory)
        for (SaleItem item : existing.getSaleItems()) {
            if (item.getItemVariant() == null) continue;
            stockService.deductStock(item.getItemVariant().getId(), item.getQty(), "Sale Completion", "Sale #" + existing.getInvoiceNo());
        }

        // Totals and Rounding with Bill-Level Adjustments (Issue 2 Fix)
        BigDecimal totalBeforeRoundOff = totalTaxableValue.add(totalGSTAmount);

        BigDecimal invoiceDisc = (dto.getInvoiceDiscount() != null && dto.getInvoiceDiscount().compareTo(ZERO) > 0)
                ? dto.getInvoiceDiscount() : ZERO;
        BigDecimal shippingCharges = ZERO;
        BigDecimal otherCharges = (dto.getOtherCharges() != null && dto.getOtherCharges().compareTo(ZERO) > 0)
                ? dto.getOtherCharges() : ZERO;
        // Include shippingCharges in total only when collected by the shop (SHOP = store bills customer)
        if (dto.getDelivery() != null
                && com.desitech.vyaparsathi.delivery.enums.DeliveryPaidBy.SHOP
                        .equals(dto.getDelivery().getDeliveryPaidBy())) {
            shippingCharges = (dto.getShippingCharges() != null) ? dto.getShippingCharges() : ZERO;
        }

        BigDecimal grandTotal = totalBeforeRoundOff
                .subtract(invoiceDisc)
                .add(shippingCharges)
                .add(otherCharges)
                .max(ZERO);
        BigDecimal finalTotal = grandTotal.setScale(0, RoundingMode.HALF_UP);
        BigDecimal roundOff = finalTotal.subtract(grandTotal);

        existing.setTotalAmount(finalTotal);
        existing.setRoundOff(roundOff);
        existing.setInvoiceDiscount(invoiceDisc);
        existing.setShippingCharges(shippingCharges);
        existing.setOtherCharges(otherCharges);
        existing.setCustomer(customer);
        existing.setStatus(SaleStatus.COMPLETED);

        existing.setIsGstRequired(gstApplicable);
        if (dto.getSalespersonId() != null) existing.setSalespersonId(dto.getSalespersonId());
        if (dto.getNotes() != null) existing.setNotes(dto.getNotes());

        Sale saved = saleRepository.save(existing);

        // Delivery Logic
        if (dto.getDelivery() != null) {
            DeliveryDTO deliveryDTO = dto.getDelivery();
            deliveryDTO.setSaleId(saved.getId());
            deliveryDTO.setInvoiceNumber(saved.getInvoiceNo());
            if (customer != null) deliveryDTO.setCustomerName(customer.getName());
            deliveryService.createDelivery(deliveryDTO);
        }

        // Ledger Entry: Sale (CREDIT)
        if (customer != null) {
            CustomerLedgerDto ledger = new CustomerLedgerDto();
            ledger.setAmount(finalTotal);
            ledger.setType(CustomerLedgerType.CREDIT);
            ledger.setDescription("Sale #" + saved.getInvoiceNo());
            ledgerService.addEntry(customer.getId(), ledger);
        }

        // Payment Logic
        if (dto.getPaymentDetails() != null) {
            // Pre-validation: Don't allow overpayment
            BigDecimal totalPaidAmount = dto.getPaymentDetails().stream()
                    .map(p -> p.getAmount() != null ? p.getAmount() : ZERO)
                    .reduce(ZERO, BigDecimal::add);

            if (totalPaidAmount.compareTo(finalTotal) > 0) {
                throw new BusinessValidationException("Total paid cannot exceed sale amount");
            }

            for (PaymentDto p : dto.getPaymentDetails()) {
                p.setSourceId(saved.getId());
                p.setSourceType(PaymentSourceType.SALE);
                p.setCustomerId(customer != null ? customer.getId() : null);
                p.setPaymentDate(LocalDateTime.now());

                // FIX: This call now handles the Payment record AND its Ledger entry automatically
                paymentService.createPayment(p);
            }
        }

        // Log Change and Generate Invoice
        changeLogService.append("SALE", saved.getId(), com.desitech.vyaparsathi.changelog.model.ChangeLogOperation.UPDATE, mapper.toDto(saved), "LOCAL_DEVICE");

        String signedToken = jwtUtil.generateInvoiceToken(saved.getId(), saved.getInvoiceNo(), saved.getShop() != null ? saved.getShop().getId() : null);
        SaleDto result = mapper.toDto(saved);
        result.setSignedInvoiceUrl("/api/invoices/signed?token=" + signedToken);

        return result;
    }

    private SaleDueDto mapToDueDto(Sale sale, BigDecimal paidAmount, String shopName) {
        BigDecimal totalAmount = sale.getTotalAmount() != null ? sale.getTotalAmount() : ZERO;
        BigDecimal dueAmount = totalAmount.subtract(paidAmount);

        SaleDueDto dto = new SaleDueDto();
        dto.setSaleId(sale.getId());
        dto.setInvoiceNo(sale.getInvoiceNo());
        dto.setDueAmount(dueAmount);
        dto.setTotalAmount(totalAmount);
        dto.setPaidAmount(paidAmount);
        dto.setDate(sale.getDate());
        dto.setStatus(sale.getStatus().name());
        dto.setEinvoiceStatus(sale.getEinvoiceStatus());
        dto.setIrn(sale.getIrn());
        dto.setAckNo(sale.getAckNo());
        dto.setAckDate(sale.getAckDate());
        dto.setQrCodePath(sale.getQrCodePath());
        dto.setEwayBillNo(sale.getEwayBillNo());
        dto.setNotes(sale.getNotes());
        dto.setSaleType(sale.getSaleType() != null ? sale.getSaleType().name() : null);

        if (sale.getCustomer() != null) {
            Customer c = sale.getCustomer();
            dto.setCustomerId(c.getId());
            dto.setCustomerName(c.getName());
            dto.setCity(c.getCity());
            dto.setState(c.getState());
            dto.setPostalCode(c.getPostalCode());
            dto.setAddressLine1(c.getAddressLine1());
            dto.setGSTIN(c.getGstNumber());
            dto.setPhone(c.getPhone());
            dto.setShopName(shopName);
        }

        // Server-truth capability flags — mirror the guards in cancelSale / processSaleReturn.
        // Anything other than COMPLETED or PARTIALLY_RETURNED is a terminal or pre-committed
        // state (DRAFT / CANCELLED / RETURNED) where cancel and return are both no-ops.
        SaleStatus st = sale.getStatus();
        boolean actionable = st == SaleStatus.COMPLETED || st == SaleStatus.PARTIALLY_RETURNED;
        dto.setCanCancel(actionable);
        dto.setCanReturn(actionable);

        return dto;
    }

    // ── Sale-type / prefix helpers ────────────────────────────────────

    /**
     * Composition-scheme shops are legally required to issue a Bill of Supply
     * rather than a tax invoice. Otherwise, honour the DTO-supplied hint.
     */
    private com.desitech.vyaparsathi.sales.enums.SaleType effectiveSaleType(String dtoType, Shop shop) {
        if (shop != null && Boolean.TRUE.equals(shop.getIsCompositionScheme())) {
            return com.desitech.vyaparsathi.sales.enums.SaleType.BILL_OF_SUPPLY;
        }
        if ("PROFORMA".equalsIgnoreCase(dtoType)) return com.desitech.vyaparsathi.sales.enums.SaleType.PROFORMA;
        if ("BILL_OF_SUPPLY".equalsIgnoreCase(dtoType) || "BOS".equalsIgnoreCase(dtoType)) {
            return com.desitech.vyaparsathi.sales.enums.SaleType.BILL_OF_SUPPLY;
        }
        return com.desitech.vyaparsathi.sales.enums.SaleType.INVOICE;
    }

    /**
     * Number-series prefix per sale type. Distinct series keep the tax-invoice
     * sequence gap-free even when a proforma is dropped or a bill-of-supply
     * is issued alongside.
     */
    private String numberPrefixFor(com.desitech.vyaparsathi.sales.enums.SaleType type, Shop shop) {
        return switch (type) {
            case PROFORMA -> "PI";
            case BILL_OF_SUPPLY -> "BOS";
            case INVOICE -> resolveInvoicePrefix(shop);
        };
    }

    /**
     * Enforce HSN/SAC presence on every taxable line of a real tax invoice.
     * The GSTN e-invoice API rejects payloads with blank HSN, so surface the
     * problem here instead of letting IRN generation silently fail downstream.
     */
    private void validateHsnPresent(SaleDto dto) {
        List<String> missing = new ArrayList<>();
        if (dto.getItems() == null) return;
        for (SaleItemDto line : dto.getItems()) {
            if (line.getItemVariantId() != null) {
                // Catalog variant — resolve and check its own HSN.
                itemVariantRepository.findById(line.getItemVariantId()).ifPresent(v -> {
                    if (v.getHsn() == null || v.getHsn().isBlank()) {
                        missing.add(line.getItemName() != null ? line.getItemName() : ("variant #" + v.getId()));
                    }
                });
            } else {
                // Custom line — DTO must carry the HSN/SAC itself.
                if (line.getCustomHsnSac() == null || line.getCustomHsnSac().isBlank()) {
                    missing.add(line.getCustomItemName() != null ? line.getCustomItemName() : "custom line");
                }
            }
        }
        if (!missing.isEmpty()) {
            throw new BusinessValidationException(
                    "HSN/SAC code is required on every line for a tax invoice. Missing on: "
                            + String.join(", ", missing));
        }
    }

    /**
     * Converts a PROFORMA sale into a real INVOICE sale.
     *
     * <p>A new Sale row is created (fresh {@code INVOICE} number from the shop's
     * real sequence), stock is deducted, ledger CREDIT is posted, delivery is
     * created if the source proforma had one. The source proforma stays intact
     * with a back-link via the new invoice's {@link Sale#proformaSourceSale}.
     *
     * <p>Idempotency: the DB unique semantics of one proforma → at most one
     * invoice is enforced by checking whether any Sale already references the
     * given proforma via {@code proforma_source_sale_id}. If found, this
     * throws — the caller must delete/void the old invoice first.
     */
    @Transactional
    @LogAudit(action = "CONVERT_PROFORMA_TO_INVOICE", entity = "SALE")
    @CheckSubscriptionLimit("SALES")
    public SaleDto convertProformaToInvoice(Long proformaSaleId) {
        Sale proforma = saleRepository.findById(proformaSaleId)
                .orElseThrow(() -> new EntityNotFoundAppException("Sale", proformaSaleId));

        if (proforma.getSaleType() != com.desitech.vyaparsathi.sales.enums.SaleType.PROFORMA) {
            throw new BusinessValidationException("Sale " + proforma.getInvoiceNo() + " is not a proforma");
        }

        // Guard against double conversion — indexed exists-query, no full-table scan.
        if (saleRepository.existsByProformaSourceSale_Id(proforma.getId())) {
            throw new BusinessValidationException(
                    "Proforma " + proforma.getInvoiceNo() + " has already been converted to a real invoice");
        }

        // Build a SaleDto that mirrors the proforma, but as a real INVOICE
        SaleDto sd = new SaleDto();
        if (proforma.getCustomer() != null) {
            com.desitech.vyaparsathi.customer.dto.CustomerDto cd =
                    new com.desitech.vyaparsathi.customer.dto.CustomerDto();
            cd.setId(proforma.getCustomer().getId());
            cd.setName(proforma.getCustomer().getName());
            sd.setCustomer(cd);
        }
        sd.setDate(LocalDateTime.now());
        sd.setIsGstRequired(proforma.getIsGstRequired());
        sd.setInvoiceDiscount(proforma.getInvoiceDiscount());
        sd.setShippingCharges(proforma.getShippingCharges());
        sd.setOtherCharges(proforma.getOtherCharges());
        sd.setSaleType("INVOICE"); // explicit — real invoice this time
        sd.setStatus(SaleStatus.COMPLETED.name());

        List<SaleItemDto> items = new ArrayList<>();
        for (SaleItem si : proforma.getSaleItems()) {
            SaleItemDto s = new SaleItemDto();
            s.setItemVariantId(si.getItemVariant() != null ? si.getItemVariant().getId() : null);
            s.setItemName(si.getItemVariant() != null && si.getItemVariant().getItem() != null
                    ? si.getItemVariant().getItem().getName() : si.getCustomItemName());
            s.setQty(si.getQty());
            s.setUnitPrice(si.getUnitPrice());
            s.setDiscount(si.getDiscount() != null ? si.getDiscount() : ZERO);
            if (si.getGstType() != null) s.setGstRate(si.getGstType().getRateAsInt());
            if (si.getItemVariant() == null) {
                s.setCustomItemName(si.getCustomItemName());
                s.setCustomDescription(si.getCustomDescription());
                s.setCustomHsnSac(si.getCustomHsnSac());
                s.setCustomUnit(si.getCustomUnit());
            }
            items.add(s);
        }
        sd.setItems(items);

        SaleDto newInvoiceDto = createSale(sd);

        // Link the new invoice back to its source proforma
        Sale newInvoice = saleRepository.findById(newInvoiceDto.getId())
                .orElseThrow(() -> new IllegalStateException("New invoice " + newInvoiceDto.getId() + " vanished"));
        newInvoice.setProformaSourceSale(proforma);
        saleRepository.save(newInvoice);

        logger.info("Converted proforma {} → invoice {}", proforma.getInvoiceNo(), newInvoice.getInvoiceNo());
        newInvoiceDto.setProformaSourceSaleId(proforma.getId());
        newInvoiceDto.setProformaSourceInvoiceNo(proforma.getInvoiceNo());
        return newInvoiceDto;
    }

    /**
     * Determines the invoice prefix for a shop and normalizes it.
     *
     * <p>Prefers {@code shop.invoicePrefix}, falls back to {@code shop.code}, and
     * defaults to {@code "INV"} if neither is set. The result is:
     *   <ul>
     *     <li>trimmed and upper-cased for consistency</li>
     *     <li>capped at 10 characters so the assembled invoice number
     *         ({@code PREFIX/YY-YY/NNNNN}) stays visually compact and prints
     *         cleanly on a standard invoice header</li>
     *   </ul>
     */
    private String resolveInvoicePrefix(Shop shop) {
        String raw = (shop != null && shop.getInvoicePrefix() != null && !shop.getInvoicePrefix().isBlank())
                ? shop.getInvoicePrefix()
                : (shop != null && shop.getCode() != null && !shop.getCode().isBlank()
                        ? shop.getCode() : "INV");
        String normalized = raw.trim().toUpperCase();
        if (normalized.length() > 10) {
            normalized = normalized.substring(0, 10);
        }
        return normalized;
    }

    /**
     * Enforce the two customer-level credit gates before persisting a
     * receivable sale:
     *
     * <ol>
     *   <li><b>Credit hold</b> — a boolean flag on the customer that
     *       pauses all new invoices regardless of amount. Set by
     *       accounts staff after a bounced cheque, open dispute,
     *       KYC lapse, etc.</li>
     *   <li><b>Credit limit</b> — a rupee ceiling on total
     *       outstanding receivable. If current outstanding + this
     *       new sale would exceed the ceiling, the sale is refused.
     *       creditLimit ≤ 0 is treated as "no ceiling".</li>
     * </ol>
     *
     * Both gates throw {@link ApplicationException} with an actionable
     * message the FE surfaces directly to the caller. There is
     * intentionally no override at this layer — an OWNER who wants
     * to bypass the gate must first clear the hold or raise the
     * limit on the customer profile, which is an audited change.
     */
    private void enforceCreditGate(Customer customer, java.math.BigDecimal saleTotal) {
        if (Boolean.TRUE.equals(customer.getCreditHold())) {
            throw new com.desitech.vyaparsathi.common.exception.ApplicationException(
                    "Cannot create invoice: customer '" + customer.getName() +
                    "' is on credit hold. Clear the hold from their profile before invoicing.");
        }
        java.math.BigDecimal limit = customer.getCreditLimit();
        if (limit == null || limit.signum() <= 0) {
            return; // no ceiling configured
        }
        java.math.BigDecimal currentOutstanding = saleRepository.sumOutstandingByCustomerId(customer.getId());
        if (currentOutstanding == null) currentOutstanding = java.math.BigDecimal.ZERO;
        java.math.BigDecimal projected = currentOutstanding.add(saleTotal == null ? java.math.BigDecimal.ZERO : saleTotal);
        if (projected.compareTo(limit) > 0) {
            throw new com.desitech.vyaparsathi.common.exception.ApplicationException(
                    "Credit-limit breach: customer '" + customer.getName() + "' has ₹" + currentOutstanding +
                    " outstanding; this invoice for ₹" + saleTotal + " would push them past their ₹" + limit +
                    " limit. Collect payment first, raise the limit on their profile, or split the invoice.");
        }
    }
}