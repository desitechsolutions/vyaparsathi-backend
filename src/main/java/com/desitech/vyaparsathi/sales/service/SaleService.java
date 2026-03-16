package com.desitech.vyaparsathi.sales.service;

import com.desitech.vyaparsathi.common.annotations.CheckSubscriptionLimit;
import com.desitech.vyaparsathi.common.annotations.LogAudit;
import com.desitech.vyaparsathi.audit.helper.AuditHelper;
import com.desitech.vyaparsathi.auth.security.JwtUtil;
import com.desitech.vyaparsathi.changelog.service.ChangeLogService;
import com.desitech.vyaparsathi.common.configs.TenantContext;
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
import com.desitech.vyaparsathi.invoice.service.InvoiceService;
import com.desitech.vyaparsathi.shop.entity.Shop;
import com.desitech.vyaparsathi.shop.repository.ShopRepository;
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

    /** Scale used when converting dispensing-unit quantities to stock-unit quantities (for loose medicine). */
    private static final int STOCK_QUANTITY_SCALE = 6;

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

    @Transactional
    @LogAudit(action = "CREATE_SALE", entity = "SALE")
    @CheckSubscriptionLimit("SALES")
    public SaleDto createSale(SaleDto dto) {
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

        for (SaleItemDto itemDto : dto.getItems()) {
            ItemVariant itemVariant = itemVariantRepository.findById(itemDto.getItemVariantId())
                    .orElseThrow(() -> new EntityNotFoundAppException("Item Variant", itemDto.getItemVariantId()));

            BigDecimal stockQty = toStockQty(itemVariant, itemDto.getQty(), itemDto);
            if (!stockService.isStockAvailable(itemDto.getItemVariantId(), stockQty)) {
                logger.warn("Insufficient stock for item: {}", itemDto.getItemName());
                throw new InsufficientStockException("Insufficient stock for item: " + itemDto.getItemName());
            }

            SaleItem saleItem = new SaleItem();
            saleItem.setItemVariant(itemVariant);
            saleItem.setQty(itemDto.getQty());
            saleItem.setUnitPrice(itemDto.getUnitPrice());
            // Persist the effective pack size so returns can reverse the same fractional qty
            saleItem.setLoosePackSize(resolveLoosePackSize(itemVariant, itemDto));
            // Pharmacy batch tracking — persist per-item batch/expiry from the frontend
            saleItem.setBatchNumber(itemDto.getBatchNumber());
            saleItem.setExpiryDate(itemDto.getExpiryDate());

            // Calculate taxable value: (Qty * Price) - Discount
            BigDecimal itemTaxableValue = itemDto.getQty()
                    .multiply(itemDto.getUnitPrice())
                    .subtract(itemDto.getDiscount() != null ? itemDto.getDiscount() : BigDecimal.ZERO);

            saleItem.setTaxableValue(itemTaxableValue);
            totalTaxableValue = totalTaxableValue.add(itemTaxableValue);

            if (Boolean.TRUE.equals(dto.getIsGstRequired()) && itemVariant.getGstRate() != null) {
                GSTType gstType = GSTType.fromRate(itemVariant.getGstRate());
                BigDecimal rate = BigDecimal.valueOf(gstType.getRate());
                BigDecimal gstAmount = itemTaxableValue.multiply(rate).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

                saleItem.setGstType(gstType);
                boolean sameState = customer != null && shop.getState().equalsIgnoreCase(customer.getState());

                if (sameState) {
                    BigDecimal half = gstAmount.divide(BigDecimal.valueOf(2), RoundingMode.HALF_UP);
                    saleItem.setCgstAmt(half);
                    saleItem.setSgstAmt(half);
                    saleItem.setIgstAmt(BigDecimal.ZERO);
                } else {
                    saleItem.setIgstAmt(gstAmount);
                    saleItem.setCgstAmt(BigDecimal.ZERO);
                    saleItem.setSgstAmt(BigDecimal.ZERO);
                }
                totalGSTAmount = totalGSTAmount.add(gstAmount);
            } else {
                saleItem.setGstType(GSTType.GST_0);
                saleItem.setCgstAmt(BigDecimal.ZERO);
                saleItem.setSgstAmt(BigDecimal.ZERO);
                saleItem.setIgstAmt(BigDecimal.ZERO);
            }
            saleItems.add(saleItem);
        }

        // 3. Generate Invoice Number
        String seq = String.format("%03d", saleRepository.count() + 1);
        String invoiceNo = shop.getCode() + "-" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM")) + "-" + seq;

        // 4. Calculate Final Totals and Rounding
        BigDecimal totalBeforeRoundOff = totalTaxableValue.add(totalGSTAmount);
        BigDecimal finalTotalAmount = totalBeforeRoundOff.setScale(0, RoundingMode.HALF_UP);
        BigDecimal roundOff = finalTotalAmount.subtract(totalBeforeRoundOff);

        // 5. Deduct Stock
        for (SaleItem item : saleItems) {
            BigDecimal stockQty = toStockQty(item.getItemVariant(), item.getQty(), item.getLoosePackSize());
            stockService.deductStock(item.getItemVariant().getId(), stockQty, "Sale Transaction", "Sale #" + invoiceNo);
        }

        // 6. Persist Sale Entity
        Sale sale = new Sale();
        sale.setInvoiceNo(invoiceNo);
        sale.setShop(shop);
        sale.setCustomer(customer);
        sale.setTotalAmount(finalTotalAmount);
        sale.setRoundOff(roundOff);
        sale.setSyncedFlag(false);
        sale.setSaleItems(saleItems);
        saleItems.forEach(si -> si.setSale(sale));

        // Pharmacy-specific fields
        sale.setDoctorName(dto.getDoctorName());
        sale.setPatientName(dto.getPatientName());
        sale.setPrescriptionNumber(dto.getPrescriptionNumber());
        sale.setDoctorRegistrationNumber(dto.getDoctorRegistrationNumber());
        sale.setIsGstRequired(Boolean.TRUE.equals(dto.getIsGstRequired()));

        Sale savedSale = saleRepository.saveAndFlush(sale);

        // 7. Handle Delivery
        if (dto.getDelivery() != null) {
            DeliveryDTO deliveryDTO = dto.getDelivery();
            deliveryDTO.setSaleId(savedSale.getId());
            deliveryDTO.setInvoiceNumber(invoiceNo);
            if (customer != null) deliveryDTO.setCustomerName(customer.getName());
            deliveryService.createDelivery(deliveryDTO);
        }

        // 8. Ledger Entry & Advance Liquidation
        if (customer != null) {
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

        String signedToken = jwtUtil.generateInvoiceToken(savedSale.getId(), sale.getInvoiceNo());
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

        BigDecimal totalReturnAmount = BigDecimal.ZERO;

        // 1. Process Items and Inventory
        for (SaleReturnDto.SaleReturnItemDto returnItem : returnDto.getReturnItems()) {
            SaleItem saleItem = sale.getSaleItems().stream()
                    .filter(si -> si.getItemVariant().getId().equals(returnItem.getSaleItemId()))
                    .findFirst()
                    .orElseThrow(() -> new EntityNotFoundAppException("Sale Item", returnItem.getSaleItemId()));

            BigDecimal currentReturned = saleItem.getReturnedQty() != null ? saleItem.getReturnedQty() : BigDecimal.ZERO;
            BigDecimal requestedQty = returnItem.getReturnQuantity();

            totalReturnAmount = totalReturnAmount.add(saleItem.getUnitPrice().multiply(requestedQty));
            saleItem.setReturnedQty(currentReturned.add(requestedQty));
            saleItem.setReturned(true);

            StockAdjustmentDto adjustment = new StockAdjustmentDto();
            adjustment.setItemVariantId(saleItem.getItemVariant().getId());
            // Convert returned dispensing qty back to stock units using the same pack size
            // that was active when the original sale was made (stored on the SaleItem).
            BigDecimal stockQty = toStockQty(saleItem.getItemVariant(), requestedQty, saleItem.getLoosePackSize());
            adjustment.setAdjustmentQuantity(stockQty);
            adjustment.setReason("Return: Inv #" + sale.getInvoiceNo());
            stockService.adjustStock(adjustment);
        }

        // 2. Calculate the Debt vs. Cash situation
        BigDecimal totalOriginalAmount = sale.getTotalAmount();
        BigDecimal paidBeforeReturn = paymentService.getTotalPaidBySaleIds(Set.of(sale.getId())).getOrDefault(sale.getId(), BigDecimal.ZERO);
        BigDecimal unpaidDebtBeforeReturn = totalOriginalAmount.subtract(paidBeforeReturn).max(BigDecimal.ZERO);

        // 3. Update Sale Header
        sale.setTotalAmount(totalOriginalAmount.subtract(totalReturnAmount).max(BigDecimal.ZERO));
        if (sale.getSaleItems().stream().allMatch(item -> item.getReturnedQty().compareTo(item.getQty()) >= 0)) {
            sale.setStatus(SaleStatus.RETURNED);
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
        auditHelper.log("PROCESS_RETURN", "SALE", sale.getId().toString(), "Returned: " + totalReturnAmount);
        changeLogService.append("SALE_RETURN", sale.getId(),
                com.desitech.vyaparsathi.changelog.model.ChangeLogOperation.RETURN, returnDto, "LOCAL_DEVICE");
    }

    @Transactional
    public void cancelSale(Long saleId, String reason) {
        BigDecimal totalPaid = ZERO;
        Sale sale = saleRepository.findById(saleId)
                .orElseThrow(() -> new EntityNotFoundAppException("Sale", saleId));

        // 1. Return Stock to Inventory
        for (SaleItem saleItem : sale.getSaleItems()) {
            StockAdjustmentDto adjustment = new StockAdjustmentDto();
            adjustment.setItemVariantId(saleItem.getItemVariant().getId());
            // For loose medicine, convert dispensing qty back to stock units using the
            // pack size stored at sale time.  Positive adjustment adds back to stock.
            BigDecimal stockQty = toStockQty(saleItem.getItemVariant(), saleItem.getQty(), saleItem.getLoosePackSize());
            adjustment.setAdjustmentQuantity(stockQty);
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

    public Optional<SaleDto> getSaleById(Long saleId) {
        return saleRepository.findById(saleId)
                .map(sale -> {
                    SaleDto dto = mapper.toDto(sale);

                    // Populate History for each item
                    if (dto.getItems() != null) {
                        for (SaleItemDto itemDto : dto.getItems()) {
                            sale.getSaleItems().stream()
                                    .filter(si -> si.getItemVariant().getId().equals(itemDto.getItemVariantId()))
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

        List<Sale> sales = saleRepository.findByDateBetween(startDate, endDate);

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

        return sales.stream()
                .map(sale -> mapToDueDto(sale, paidBySale.getOrDefault(sale.getId(), ZERO)))
                .filter(dto -> dto.getDueAmount().compareTo(ZERO) > 0)
                .collect(Collectors.toList());
    }

    public List<SaleDueDto> getSalesHistory() {
        // WARNING: For production, this should be Paginated!
        List<Sale> sales = saleRepository.findAll();

        Set<Long> saleIds = sales.stream().map(Sale::getId).collect(Collectors.toSet());
        Map<Long, BigDecimal> paidBySale = paymentService.getTotalPaidBySaleIds(saleIds);

        return sales.stream()
                .map(sale -> mapToDueDto(sale, paidBySale.getOrDefault(sale.getId(), ZERO)))
                .collect(Collectors.toList());
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

        List<SaleDueDto> dtos = salesPage.getContent().stream()
                .map(sale -> mapToDueDto(sale, paidBySale.getOrDefault(sale.getId(), ZERO)))
                .collect(Collectors.toList());

        return new PageImpl<>(dtos, pageable, salesPage.getTotalElements());
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
            String seq = String.format("%03d", saleRepository.count() + 1);
            // Ensure invoiceNo is assigned here for new drafts
            sale.setInvoiceNo("DRF-" + shop.getCode() + "-" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM")) + "-" + seq);
            sale.setStatus(SaleStatus.DRAFT);
        }

        BigDecimal totalTaxableValue = ZERO;
        List<SaleItem> itemsToUpdate = new ArrayList<>();

        for (SaleItemDto itemDto : dto.getItems()) {
            ItemVariant iv = itemVariantRepository.findById(itemDto.getItemVariantId())
                    .orElseThrow(() -> new EntityNotFoundAppException("Item Variant", itemDto.getItemVariantId()));

            SaleItem saleItem = new SaleItem();
            saleItem.setItemVariant(iv);
            saleItem.setQty(itemDto.getQty());
            saleItem.setUnitPrice(itemDto.getUnitPrice());
            saleItem.setDiscount(itemDto.getDiscount() != null ? itemDto.getDiscount() : ZERO);

            BigDecimal taxableValue = itemDto.getQty().multiply(itemDto.getUnitPrice())
                    .subtract(saleItem.getDiscount());

            saleItem.setTaxableValue(taxableValue);
            totalTaxableValue = totalTaxableValue.add(taxableValue);

            // Drafts usually have 0 tax until completed
            saleItem.setGstType(GSTType.GST_0);
            saleItem.setCgstAmt(ZERO);
            saleItem.setSgstAmt(ZERO);
            saleItem.setIgstAmt(ZERO);

            saleItem.setSale(sale);
            itemsToUpdate.add(saleItem);
        }

        sale.setShop(shop);
        sale.setCustomer(customer);
        sale.setTotalAmount(totalTaxableValue.setScale(0, RoundingMode.HALF_UP));
        sale.setRoundOff(sale.getTotalAmount().subtract(totalTaxableValue)); // Difference for ledger balancing
        sale.getSaleItems().addAll(itemsToUpdate);

        // Pharmacy-specific fields
        sale.setDoctorName(dto.getDoctorName());
        sale.setPatientName(dto.getPatientName());
        sale.setPrescriptionNumber(dto.getPrescriptionNumber());
        sale.setDoctorRegistrationNumber(dto.getDoctorRegistrationNumber());

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
    public SaleDto completeDraft(SaleDto dto) {
        if (dto.getId() == null) {
            throw new BusinessValidationException("Sale ID is required to complete a draft");
        }

        Sale existing = saleRepository.findById(dto.getId())
                .orElseThrow(() -> new EntityNotFoundAppException("Sale", dto.getId()));

        if (existing.getStatus() != SaleStatus.DRAFT) {
            throw new BusinessValidationException("Only DRAFT sales can be completed");
        }

        Customer customer = Optional.ofNullable(dto.getCustomer())
                .map(c -> customerRepository.findById(c.getId())
                        .orElseThrow(() -> new EntityNotFoundAppException("Customer", c.getId())))
                .orElse(null);

        // Clear old draft items to replace with final ones
        existing.getSaleItems().clear();

        BigDecimal totalTaxableValue = ZERO;
        BigDecimal totalGSTAmount = ZERO;

        for (SaleItemDto itemDto : dto.getItems()) {
            ItemVariant itemVariant = itemVariantRepository.findById(itemDto.getItemVariantId())
                    .orElseThrow(() -> new EntityNotFoundAppException("Item Variant", itemDto.getItemVariantId()));

            BigDecimal stockQty = toStockQty(itemVariant, itemDto.getQty(), itemDto);
            if (!stockService.isStockAvailable(itemDto.getItemVariantId(), stockQty)) {
                throw new InsufficientStockException("Insufficient stock for item: " + itemDto.getItemName());
            }

            SaleItem saleItem = new SaleItem();
            saleItem.setSale(existing);
            saleItem.setItemVariant(itemVariant);
            saleItem.setQty(itemDto.getQty());
            saleItem.setUnitPrice(itemDto.getUnitPrice());
            saleItem.setDiscount(itemDto.getDiscount() != null ? itemDto.getDiscount() : ZERO);
            // Persist the effective pack size so returns can reverse the same fractional qty
            saleItem.setLoosePackSize(resolveLoosePackSize(itemVariant, itemDto));
            // Pharmacy batch tracking — persist per-item batch/expiry from the frontend
            saleItem.setBatchNumber(itemDto.getBatchNumber());
            saleItem.setExpiryDate(itemDto.getExpiryDate());

            // FIX: Taxable value calculation for EVERY item (Required for Sales Volume Reports)
            BigDecimal taxableValue = itemDto.getQty()
                    .multiply(itemDto.getUnitPrice())
                    .subtract(saleItem.getDiscount());

            saleItem.setTaxableValue(taxableValue);
            totalTaxableValue = totalTaxableValue.add(taxableValue);

            if (Boolean.TRUE.equals(dto.getIsGstRequired()) && itemVariant.getGstRate() != null) {
                GSTType gstType = GSTType.fromRate(itemVariant.getGstRate());
                BigDecimal gstAmount = taxableValue
                        .multiply(BigDecimal.valueOf(gstType.getRate()))
                        .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

                saleItem.setGstType(gstType);
                boolean sameState = customer != null && existing.getShop().getState().equalsIgnoreCase(customer.getState());

                if (sameState) {
                    BigDecimal half = gstAmount.divide(BigDecimal.valueOf(2), RoundingMode.HALF_UP);
                    saleItem.setCgstAmt(half);
                    saleItem.setSgstAmt(half);
                    saleItem.setIgstAmt(ZERO);
                } else {
                    saleItem.setIgstAmt(gstAmount);
                    saleItem.setCgstAmt(ZERO);
                    saleItem.setSgstAmt(ZERO);
                }
                totalGSTAmount = totalGSTAmount.add(gstAmount);
            } else {
                saleItem.setGstType(GSTType.GST_0);
                saleItem.setCgstAmt(ZERO);
                saleItem.setSgstAmt(ZERO);
                saleItem.setIgstAmt(ZERO);
            }
            existing.getSaleItems().add(saleItem);
        }

        // Deduct stock
        for (SaleItem item : existing.getSaleItems()) {
            BigDecimal stockQty = toStockQty(item.getItemVariant(), item.getQty(), item.getLoosePackSize());
            stockService.deductStock(item.getItemVariant().getId(), stockQty, "Sale Completion", "Sale #" + existing.getInvoiceNo());
        }

        // Totals and Rounding
        BigDecimal totalBeforeRoundOff = totalTaxableValue.add(totalGSTAmount);
        BigDecimal finalTotal = totalBeforeRoundOff.setScale(0, RoundingMode.HALF_UP);
        BigDecimal roundOff = finalTotal.subtract(totalBeforeRoundOff);

        existing.setTotalAmount(finalTotal);
        existing.setRoundOff(roundOff);
        existing.setCustomer(customer);
        existing.setStatus(SaleStatus.COMPLETED);

        // Pharmacy-specific fields
        existing.setDoctorName(dto.getDoctorName());
        existing.setPatientName(dto.getPatientName());
        existing.setPrescriptionNumber(dto.getPrescriptionNumber());
        existing.setDoctorRegistrationNumber(dto.getDoctorRegistrationNumber());
        existing.setIsGstRequired(Boolean.TRUE.equals(dto.getIsGstRequired()));

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

        String signedToken = jwtUtil.generateInvoiceToken(saved.getId(), saved.getInvoiceNo());
        SaleDto result = mapper.toDto(saved);
        result.setSignedInvoiceUrl("/api/invoices/signed?token=" + signedToken);

        return result;
    }

    private SaleDueDto mapToDueDto(Sale sale, BigDecimal paidAmount) {
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
            dto.setShopName(shopRepository.findById(TenantContext.getCurrentShopId()).map(Shop::getName).orElse(""));
        }
        return dto;
    }

    /**
     * Converts a dispensing-unit quantity to a stock-unit quantity for loose medicines.
     * <p>
     * This is the <em>legacy fallback</em> overload — it uses only the ItemVariant's
     * database configuration ({@link ItemVariant#getIsLooseMedicine()} and
     * {@link ItemVariant#getPackSize()}).  Prefer the overloads that accept an explicit
     * {@code loosePackSize} or a {@link SaleItemDto} when those values are available,
     * because the ItemVariant may not yet be configured as a loose medicine even when the
     * pharmacist chooses to dispense loose at the point of sale.
     *
     * @param variant       the item variant being sold/returned
     * @param dispensingQty quantity in the dispensing/selling unit (e.g. tablets)
     * @return equivalent quantity in the stock unit (e.g. strips)
     */
    private BigDecimal toStockQty(ItemVariant variant, BigDecimal dispensingQty) {
        if (Boolean.TRUE.equals(variant.getIsLooseMedicine())
                && variant.getPackSize() != null
                && variant.getPackSize().compareTo(BigDecimal.ZERO) > 0) {
            return dispensingQty.divide(variant.getPackSize(), STOCK_QUANTITY_SCALE, RoundingMode.HALF_UP);
        }
        return dispensingQty;
    }

    /**
     * Returns {@code true} when {@code packSize} represents a valid positive pack size —
     * the single source of truth for that check, shared by all {@code toStockQty} overloads
     * and {@link #resolveLoosePackSize}.
     */
    private static boolean isValidPackSize(BigDecimal packSize) {
        return packSize != null && packSize.compareTo(BigDecimal.ZERO) > 0;
    }

    /**
     * Converts dispensing qty to stock qty, preferring an explicitly stored
     * {@code loosePackSize} over the ItemVariant's default settings.
     * <p>
     * Used when a {@link SaleItem} entity is available (stock deduction loop, cancel,
     * and return).  The stored {@code loosePackSize} was captured from the frontend at
     * sale-creation time, so it reflects the pack size the pharmacist actually used —
     * even if the ItemVariant is not pre-configured as a loose medicine.
     *
     * @param variant         the item variant
     * @param dispensingQty   quantity in dispensing units
     * @param loosePackSize   pack size persisted on the SaleItem, or {@code null} for full-pack sales
     * @return equivalent quantity in stock units
     */
    private BigDecimal toStockQty(ItemVariant variant, BigDecimal dispensingQty, BigDecimal loosePackSize) {
        if (isValidPackSize(loosePackSize)) {
            return dispensingQty.divide(loosePackSize, STOCK_QUANTITY_SCALE, RoundingMode.HALF_UP);
        }
        return toStockQty(variant, dispensingQty);
    }

    /**
     * Converts dispensing qty to stock qty, preferring the sale-time pack size from
     * the incoming {@link SaleItemDto} over the ItemVariant's database settings.
     * <p>
     * Used during sale creation / draft completion, where we have the DTO available.
     * This allows the pharmacist to sell loose medicine even if
     * {@link ItemVariant#getIsLooseMedicine()} is not yet set in the database.
     *
     * @param variant       the item variant
     * @param dispensingQty quantity in dispensing units
     * @param dto           the incoming sale-item DTO
     * @return equivalent quantity in stock units
     */
    private BigDecimal toStockQty(ItemVariant variant, BigDecimal dispensingQty, SaleItemDto dto) {
        if (Boolean.TRUE.equals(dto.getIsLooseSale()) && isValidPackSize(dto.getLoosePackSize())) {
            return dispensingQty.divide(dto.getLoosePackSize(), STOCK_QUANTITY_SCALE, RoundingMode.HALF_UP);
        }
        return toStockQty(variant, dispensingQty);
    }

    /**
     * Determines the effective pack size to persist on a new {@link SaleItem}.
     * Prefers the explicit value from the frontend DTO; falls back to the ItemVariant's
     * database configuration.  Returns {@code null} for full-pack sales.
     */
    private BigDecimal resolveLoosePackSize(ItemVariant variant, SaleItemDto dto) {
        if (Boolean.TRUE.equals(dto.getIsLooseSale()) && isValidPackSize(dto.getLoosePackSize())) {
            return dto.getLoosePackSize();
        }
        if (Boolean.TRUE.equals(variant.getIsLooseMedicine()) && isValidPackSize(variant.getPackSize())) {
            return variant.getPackSize();
        }
        return null;
    }
}