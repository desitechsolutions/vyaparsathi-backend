package com.desitech.vyaparsathi.sales.service;

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
import com.desitech.vyaparsathi.delivery.service.DeliveryService;
import com.desitech.vyaparsathi.inventory.dto.StockAdjustmentDto;
import com.desitech.vyaparsathi.inventory.entity.ItemVariant;
import com.desitech.vyaparsathi.inventory.repository.ItemVariantRepository;
import com.desitech.vyaparsathi.inventory.service.StockService;
import com.desitech.vyaparsathi.payment.dto.PaymentDto;
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
import com.desitech.vyaparsathi.sales.service.invoice.InvoiceService2;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

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
    private InvoiceService2 invoiceService;
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
    private JwtUtil jwtUtil;

    @Transactional
    public SaleDto createSale(SaleDto dto) {
        Shop shop = shopRepository.findById(TenantContext.getCurrentShopId()).orElseThrow(() -> new EntityNotFoundAppException("Shop", TenantContext.getCurrentShopId()));
        Optional<Customer> customerOpt = Optional.ofNullable(dto.getCustomer().getId()).flatMap(customerRepository::findById);
        Customer customer = customerOpt.orElse(null);

        List<SaleItem> saleItems = new ArrayList<>();
        BigDecimal totalTaxableValue = BigDecimal.ZERO;
        BigDecimal totalGSTAmount = BigDecimal.ZERO;

        for (SaleItemDto itemDto : dto.getItems()) {
            if (!stockService.isStockAvailable(itemDto.getItemVariantId(), itemDto.getQty())) {
                logger.warn("Insufficient stock for item: {}", itemDto.getItemName());
                throw new InsufficientStockException("Insufficient stock for item: " + itemDto.getItemName());
            }

            ItemVariant itemVariant = itemVariantRepository.findById(itemDto.getItemVariantId())
                    .orElseThrow(() -> new EntityNotFoundAppException("Item Variant", itemDto.getItemVariantId()));

            SaleItem saleItem = new SaleItem();
            saleItem.setItemVariant(itemVariant);
            saleItem.setQty(itemDto.getQty());
            saleItem.setUnitPrice(itemDto.getUnitPrice());

            if (Boolean.TRUE.equals(dto.getIsGstRequired()) && itemVariant.getGstRate() != null) {
                GSTType gstType = GSTType.fromRate(itemVariant.getGstRate());

                BigDecimal taxableValue = itemDto.getQty()
                        .multiply(itemDto.getUnitPrice())
                        .subtract(itemDto.getDiscount() != null ? itemDto.getDiscount() : BigDecimal.ZERO);

                BigDecimal gstAmount = taxableValue
                        .multiply(BigDecimal.valueOf(gstType.getRate()))
                        .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

                saleItem.setTaxableValue(taxableValue);
                saleItem.setGstType(gstType);

                boolean sameState = customer != null && shop.getState().equals(customer.getState());
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

                totalTaxableValue = totalTaxableValue.add(taxableValue);
                totalGSTAmount = totalGSTAmount.add(gstAmount);
            } else {
                saleItem.setTaxableValue(BigDecimal.ZERO);
                saleItem.setGstType(GSTType.GST_0);
                saleItem.setCgstAmt(BigDecimal.ZERO);
                saleItem.setSgstAmt(BigDecimal.ZERO);
                saleItem.setIgstAmt(BigDecimal.ZERO);
            }

            saleItems.add(saleItem);
        }

        String seq = String.format("%03d", saleRepository.count() + 1);
        String invoiceNo = shop.getCode() + "-" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM")) + "-" + seq;

        // Deduct stock for all items after successful validation
        for (SaleItemDto itemDto : dto.getItems()) {
            stockService.deductStock(itemDto.getItemVariantId(), itemDto.getQty(),
                    "Sale Transaction", "Sale #" + invoiceNo);
        }

        BigDecimal totalAmountBeforeRoundOff;
        if (Boolean.TRUE.equals(dto.getIsGstRequired())) {
            totalAmountBeforeRoundOff = totalTaxableValue.add(totalGSTAmount);
        } else {
            totalAmountBeforeRoundOff = dto.getItems().stream()
                    .map(item -> item.getQty().multiply(item.getUnitPrice()).subtract(item.getDiscount() != null ? item.getDiscount() : BigDecimal.ZERO))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }
        BigDecimal roundOff = totalAmountBeforeRoundOff.subtract(totalAmountBeforeRoundOff.setScale(0, RoundingMode.HALF_UP));
        BigDecimal finalTotalAmount = totalAmountBeforeRoundOff.setScale(0, RoundingMode.HALF_UP);

        Sale sale = new Sale();
        sale.setInvoiceNo(invoiceNo);
        sale.setShop(shop);
        sale.setCustomer(customer);
        sale.setTotalAmount(finalTotalAmount);
        sale.setRoundOff(roundOff.negate());
        sale.setSyncedFlag(false);
        sale.setSaleItems(saleItems);
        saleItems.forEach(si -> si.setSale(sale));

        saleRepository.save(sale);

        if (dto.getDelivery() != null) {
            DeliveryDTO deliveryDTO = dto.getDelivery();
            deliveryDTO.setSaleId(sale.getId());
            deliveryDTO.setInvoiceNumber(invoiceNo);
            if (customer != null) {
                deliveryDTO.setCustomerName(customer.getName());
            }
            deliveryService.createDelivery(deliveryDTO);
        }

        if (customer != null) {
            CustomerLedgerDto saleLedgerDto = new CustomerLedgerDto();
            saleLedgerDto.setAmount(finalTotalAmount);
            saleLedgerDto.setType(CustomerLedgerType.CREDIT);
            saleLedgerDto.setDescription("Sale #" + invoiceNo);
            ledgerService.addEntry(customer.getId(), saleLedgerDto);
        }

        if (dto.getPaymentDetails() != null) {
            BigDecimal totalPaid = BigDecimal.ZERO;
            for (PaymentDto paymentDTO : dto.getPaymentDetails()) {
                if (paymentDTO.getAmount() == null) {
                    logger.error("Payment amount cannot be null for sale DTO: {}", dto);
                    throw new BusinessValidationException("Payment amount cannot be null");
                }
                paymentDTO.setSourceId(sale.getId());
                paymentDTO.setSourceType(PaymentSourceType.SALE);
                paymentDTO.setCustomerId(customer != null ? customer.getId() : null);
                paymentDTO.setPaymentDate(LocalDateTime.now());
                paymentService.createPayment(paymentDTO);
                totalPaid = totalPaid.add(paymentDTO.getAmount());

                if (customer != null) {
                    CustomerLedgerDto ledgerDto = new CustomerLedgerDto();
                    ledgerDto.setAmount(paymentDTO.getAmount());
                    ledgerDto.setType(CustomerLedgerType.DEBIT);
                    ledgerDto.setDescription("Payment for Sale #" + invoiceNo + " (" + paymentDTO.getPaymentMethod() + ")");
                    ledgerService.addEntry(customer.getId(), ledgerDto);
                }
            }

            BigDecimal unpaidAmount = finalTotalAmount.subtract(totalPaid);
            if (unpaidAmount.compareTo(BigDecimal.ZERO) < 0) {
                logger.error("Total paid ({}) exceeds sale amount ({})", totalPaid, finalTotalAmount);
                throw new BusinessValidationException("Total paid cannot exceed sale amount");
            }
        }

        changeLogService.append("SALE", sale.getId(), com.desitech.vyaparsathi.changelog.model.ChangeLogOperation.CREATE, sale, "LOCAL_DEVICE");

        // Generate signed invoice URL
        String signedToken = jwtUtil.generateInvoiceToken(sale.getId(), sale.getInvoiceNo());
        String signedUrl = "/api/invoices/signed?token=" + signedToken;
        SaleDto resultDto = mapper.toDto(sale);
        resultDto.setSignedInvoiceUrl(signedUrl);
        logger.info("Generated signed invoice URL for sale ID: {}, invoice: {}", sale.getId(), sale.getInvoiceNo());
        return resultDto;
    }

    @Transactional
    public void processSaleReturn(SaleReturnDto returnDto) {
        Sale sale = saleRepository.findById(returnDto.getSaleId())
                .orElseThrow(() -> new EntityNotFoundAppException("Sale", returnDto.getSaleId()));

        BigDecimal totalReturnAmount = BigDecimal.ZERO;

        for (SaleReturnDto.SaleReturnItemDto returnItem : returnDto.getReturnItems()) {
            SaleItem saleItem = sale.getSaleItems().stream()
                    .filter(si -> si.getId().equals(returnItem.getSaleItemId()))
                    .findFirst()
                    .orElseThrow(() -> new EntityNotFoundAppException("Sale item", returnItem.getSaleItemId()));

            if (returnItem.getReturnQuantity().compareTo(saleItem.getQty()) > 0) {
                logger.error("Return quantity ({}) exceeds original quantity ({}) for sale item {}", returnItem.getReturnQuantity(), saleItem.getQty(), saleItem.getId());
                throw new BusinessValidationException("Return quantity cannot exceed original quantity");
            }

            BigDecimal itemReturnAmount = saleItem.getUnitPrice().multiply(returnItem.getReturnQuantity());
            totalReturnAmount = totalReturnAmount.add(itemReturnAmount);

            // Return stock to inventory by creating a positive adjustment
            StockAdjustmentDto adjustment = new StockAdjustmentDto();
            adjustment.setItemVariantId(saleItem.getItemVariant().getId());
            adjustment.setAdjustmentQuantity(returnItem.getReturnQuantity()); // Positive quantity
            adjustment.setReason("Return from Sale #" + sale.getInvoiceNo());
            stockService.adjustStock(adjustment);

            saleItem.setQty(saleItem.getQty().subtract(returnItem.getReturnQuantity()));
        }

        sale.setTotalAmount(sale.getTotalAmount().subtract(totalReturnAmount));
        logger.info("Processed sale return for sale ID {}: return amount {}", sale.getId(), totalReturnAmount);

        saleRepository.save(sale);

        if (returnDto.isRefundPayment() && sale.getCustomer() != null) {
            CustomerLedgerDto ledgerDto = new CustomerLedgerDto();
            ledgerDto.setAmount(totalReturnAmount);
            ledgerDto.setType(CustomerLedgerType.DEBIT);
            ledgerDto.setDescription("Return for Sale #" + sale.getInvoiceNo() +
                    (returnDto.getReason() != null ? " - " + returnDto.getReason() : ""));
            ledgerService.addEntry(sale.getCustomer().getId(), ledgerDto);
        }

        changeLogService.append("SALE_RETURN", sale.getId(), com.desitech.vyaparsathi.changelog.model.ChangeLogOperation.RETURN, returnDto, "LOCAL_DEVICE");
    }

    @Transactional
    public void cancelSale(Long saleId, String reason) {
        Sale sale = saleRepository.findById(saleId)
                .orElseThrow(() -> new EntityNotFoundAppException("Sale", saleId));

        for (SaleItem saleItem : sale.getSaleItems()) {
            // Restore stock by creating a positive adjustment
            StockAdjustmentDto adjustment = new StockAdjustmentDto();
            adjustment.setItemVariantId(saleItem.getItemVariant().getId());
            adjustment.setAdjustmentQuantity(saleItem.getQty()); // Positive quantity
            adjustment.setReason("Cancelled Sale #" + sale.getInvoiceNo());
            stockService.adjustStock(adjustment);
        }

        if (sale.getCustomer() != null) {
            CustomerLedgerDto reverseDto = new CustomerLedgerDto();
            reverseDto.setAmount(sale.getTotalAmount());
            reverseDto.setType(CustomerLedgerType.DEBIT);
            reverseDto.setDescription("Cancelled Sale #" + sale.getInvoiceNo() +
                    (reason != null ? " - " + reason : ""));
            ledgerService.addEntry(sale.getCustomer().getId(), reverseDto);

            List<PaymentDto> payments = paymentService.getPaymentsBySource(PaymentSourceType.SALE, sale.getId());
            for (PaymentDto payment : payments) {
                CustomerLedgerDto paymentReverseDto = new CustomerLedgerDto();
                paymentReverseDto.setAmount(payment.getAmount());
                paymentReverseDto.setType(CustomerLedgerType.CREDIT);
                paymentReverseDto.setDescription("Cancelled Payment for Sale #" + sale.getInvoiceNo() +
                        " (" + payment.getPaymentMethod() + ")");
                ledgerService.addEntry(sale.getCustomer().getId(), paymentReverseDto);
            }
        }

        sale.setTotalAmount(BigDecimal.ZERO);
        saleRepository.save(sale);
        logger.info("Cancelled sale with ID {}", saleId);

        changeLogService.append("SALE_CANCEL", sale.getId(), com.desitech.vyaparsathi.changelog.model.ChangeLogOperation.CANCEL,
                java.util.Map.of("reason", reason), "LOCAL_DEVICE");
    }

    // NOTE: The getSalesProfitReport method has been removed from this service.
    // Profit reporting requires COGS calculation, which is now correctly handled
    // by the ReportService and COGSCalculationService to ensure real-time accuracy.

    public Optional<SaleDto> getSaleById(Long id) {
        return saleRepository.findById(id).map(mapper::toDto);
    }

    public List<SaleDto> listSales(LocalDateTime startDate, LocalDateTime endDate) {
        if (startDate == null) {
            startDate = LocalDateTime.of(1970, 1, 1, 0, 0);
        }
        if (endDate == null) {
            endDate = LocalDateTime.now();
        }
        else {
            endDate = endDate.with(LocalTime.MAX);
        }
        List<Sale> sales = saleRepository.findByDateBetween(startDate, endDate);

        List<SaleDto> saleDtos = new ArrayList<>();
        for (Sale sale : sales) {
            SaleDto dto = mapper.toDto(sale);
            BigDecimal totalAmount = sale.getTotalAmount() != null ? sale.getTotalAmount() : BigDecimal.ZERO;
            List<PaymentDto> payments = paymentService.getPaymentsBySource(PaymentSourceType.SALE, sale.getId());
            BigDecimal paidAmount = payments.stream()
                    .map(p -> p.getAmount() != null ? p.getAmount() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal dueAmount = totalAmount.subtract(paidAmount);
            dto.setPaidAmount(paidAmount);
            dto.setDueAmount(dueAmount);
            saleDtos.add(dto);
        }

        logger.info("Fetched {} sales between {} and {}", sales.size(), startDate, endDate);
        return saleDtos;
    }

    public List<SaleDueDto> getSalesWithDue() {
        List<Sale> sales = saleRepository.findAll();
        return sales.stream()
                .map(sale -> {
                    BigDecimal totalAmount = sale.getTotalAmount();
                    BigDecimal paidAmount = paymentService.getPaymentsBySource(PaymentSourceType.SALE, sale.getId()).stream()
                            .map(p -> p.getAmount() != null ? p.getAmount() : BigDecimal.ZERO)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    BigDecimal dueAmount = totalAmount.subtract(paidAmount);
                    return new SaleDueDto(
                            sale.getId(),
                            sale.getInvoiceNo(),
                            dueAmount,
                            sale.getCustomer().getId(),
                            sale.getDate(),
                            sale.getTotalAmount(),
                            paidAmount,
                            sale.getCustomer().getName(),
                            sale.getCustomer().getAddressLine1(),
                            sale.getCustomer().getCity(),
                            sale.getCustomer().getState(),
                            sale.getCustomer().getPostalCode(),
                            sale.getStatus().name()
                    );
                })
                .collect(Collectors.toList());
    }

    public SaleDueDto getSaleDueBySaleId(Long saleId) {
        return saleRepository.findById(saleId)
                .map(sale -> {
                    SaleDueDto dto = new SaleDueDto();
                    dto.setSaleId(sale.getId());
                    dto.setInvoiceNo(sale.getInvoiceNo());
                    BigDecimal dueAmount = paymentService.calculateDueAmount(sale.getId(), PaymentSourceType.SALE, sale.getTotalAmount());
                    dto.setDueAmount(dueAmount);
                    return dto;
                })
                .orElseThrow(() -> new EntityNotFoundAppException("Sale", saleId));
    }

    public Page<SaleDueDto> getDuesByCustomerId(Long customerId, Pageable pageable) {
        Page<Sale> sales = saleRepository.findByCustomerId(customerId, pageable);
        List<SaleDueDto> filtered = sales.stream()
                .map(sale -> {
                    BigDecimal totalAmount = sale.getTotalAmount();
                    BigDecimal paidAmount = paymentService.getPaymentsBySource(PaymentSourceType.SALE, sale.getId()).stream()
                            .map(p -> p.getAmount() != null ? p.getAmount() : BigDecimal.ZERO)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    BigDecimal dueAmount = totalAmount.subtract(paidAmount);
                    return new SaleDueDto(
                            sale.getId(),
                            sale.getInvoiceNo(),
                            dueAmount,
                            sale.getCustomer().getId(),
                            sale.getDate(),
                            totalAmount,
                            paidAmount,
                            sale.getCustomer().getName(),
                            sale.getCustomer().getAddressLine1(),
                            sale.getCustomer().getCity(),
                            sale.getCustomer().getState(),
                            sale.getCustomer().getPostalCode(),
                            sale.getStatus().name()
                    );
                })
                .filter(dto -> dto.getDueAmount().compareTo(BigDecimal.ZERO) > 0)
                .toList();

        return new PageImpl<>(filtered, pageable, sales.getTotalElements());
    }

    @Transactional
    public SaleDto saveOrUpdateDraft(SaleDto dto) {
        Shop shop = shopRepository.findById(TenantContext.getCurrentShopId()).orElseThrow(() -> new EntityNotFoundAppException("Shop", TenantContext.getCurrentShopId()));
        Optional<Customer> customerOpt = Optional.ofNullable(dto.getCustomer().getId()).flatMap(customerRepository::findById);
        Customer customer = customerOpt.orElse(null);

        String invoiceNo = null;

        Sale sale;
        boolean isUpdate = dto.getId() != null;

        if (isUpdate) {
            sale = saleRepository.findById(dto.getId())
                    .orElseThrow(() -> new EntityNotFoundAppException("Sale", dto.getId()));
            // Optional: Ensure we only update if current status is DRAFT
            if (!"DRAFT".equals(sale.getStatus().name())) {
                throw new BusinessValidationException("Only drafts can be updated via this endpoint.");
            }
            // Clear old items for replacement
            sale.getSaleItems().clear();
        } else {
            sale = new Sale();
            String seq = String.format("%03d", saleRepository.count() + 1);
            invoiceNo = "DRF-" + shop.getCode() + "-" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM")) + "-" + seq;
            sale.setInvoiceNo(invoiceNo);
        }

        List<SaleItem> saleItems = new ArrayList<>();
        BigDecimal totalTaxableValue = BigDecimal.ZERO;

        for (SaleItemDto itemDto : dto.getItems()) {
            ItemVariant itemVariant = itemVariantRepository.findById(itemDto.getItemVariantId())
                    .orElseThrow(() -> new EntityNotFoundAppException("Item Variant", itemDto.getItemVariantId()));

            SaleItem saleItem = new SaleItem();
            saleItem.setItemVariant(itemVariant);
            saleItem.setQty(itemDto.getQty());
            saleItem.setUnitPrice(itemDto.getUnitPrice());
            saleItem.setDiscount(itemDto.getDiscount() != null ? itemDto.getDiscount() : BigDecimal.ZERO);

            // Calculate taxable value (Simplified for Draft)
            BigDecimal taxableValue = itemDto.getQty().multiply(itemDto.getUnitPrice())
                    .subtract(saleItem.getDiscount());
            saleItem.setTaxableValue(taxableValue);
            totalTaxableValue = totalTaxableValue.add(taxableValue);

            saleItem.setGstType(GSTType.GST_0); // Default to 0 for drafts
            saleItem.setCgstAmt(BigDecimal.ZERO);
            saleItem.setSgstAmt(BigDecimal.ZERO);
            saleItem.setIgstAmt(BigDecimal.ZERO);

            saleItem.setSale(sale);
            saleItems.add(saleItem);
        }

        sale.setShop(shop);
        sale.setCustomer(customer);
        sale.setStatus(SaleStatus.DRAFT);
        sale.setTotalAmount(totalTaxableValue.setScale(0, RoundingMode.HALF_UP));
        sale.setRoundOff(totalTaxableValue.subtract(sale.getTotalAmount()));
        sale.getSaleItems().addAll(saleItems);

        saleRepository.save(sale);

        if (dto.getDelivery() != null) {
            DeliveryDTO deliveryDTO = dto.getDelivery();
            deliveryDTO.setSaleId(sale.getId());
            deliveryDTO.setInvoiceNumber(invoiceNo);
            if (customer != null) {
                deliveryDTO.setCustomerName(customer.getName());
            }
            deliveryService.createDelivery(deliveryDTO);
        }


        // --- WE SKIP STOCK DEDUCTION, LEDGER, AND PAYMENTS HERE ---

        logger.info("Draft {} saved for customer {}", sale.getInvoiceNo(), customer != null ? customer.getName() : "Walk-in");
        return mapper.toDto(sale);
    }

    @Transactional
    public SaleDto completeDraft(SaleDto dto) {

        if (dto.getId() == null) {
            throw new BusinessValidationException("Sale ID is required to complete a draft");
        }

        Sale existing = saleRepository.findById(dto.getId())
                .orElseThrow(() -> new EntityNotFoundAppException("Sale", dto.getId()));

        if (existing.getStatus() != SaleStatus.DRAFT) {
            throw new BusinessValidationException("Only DRAFT sales can be completed");
        }

        // Load real customer from DB (same pattern as createSale)
        Customer customer = Optional.ofNullable(dto.getCustomer())
                .map(c -> customerRepository.findById(c.getId())
                        .orElseThrow(() -> new EntityNotFoundAppException("Customer", c.getId())))
                .orElse(null);

        // ===== 1) Replace items with fresh calculation (same logic as createSale but NO new invoice =====
        existing.getSaleItems().clear();

        BigDecimal totalTaxableValue = BigDecimal.ZERO;
        BigDecimal totalGSTAmount = BigDecimal.ZERO;

        for (SaleItemDto itemDto : dto.getItems()) {

            if (!stockService.isStockAvailable(itemDto.getItemVariantId(), itemDto.getQty())) {
                throw new InsufficientStockException("Insufficient stock for item: " + itemDto.getItemName());
            }

            ItemVariant itemVariant = itemVariantRepository.findById(itemDto.getItemVariantId())
                    .orElseThrow(() -> new EntityNotFoundAppException("Item Variant", itemDto.getItemVariantId()));

            SaleItem saleItem = new SaleItem();
            saleItem.setSale(existing);
            saleItem.setItemVariant(itemVariant);
            saleItem.setQty(itemDto.getQty());
            saleItem.setUnitPrice(itemDto.getUnitPrice());
            saleItem.setDiscount(itemDto.getDiscount() != null ? itemDto.getDiscount() : BigDecimal.ZERO);

            if (Boolean.TRUE.equals(dto.getIsGstRequired()) && itemVariant.getGstRate() != null) {

                GSTType gstType = GSTType.fromRate(itemVariant.getGstRate());

                BigDecimal taxableValue = itemDto.getQty()
                        .multiply(itemDto.getUnitPrice())
                        .subtract(saleItem.getDiscount());

                BigDecimal gstAmount = taxableValue
                        .multiply(BigDecimal.valueOf(gstType.getRate()))
                        .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

                saleItem.setTaxableValue(taxableValue);
                saleItem.setGstType(gstType);

                boolean sameState = customer != null &&
                        existing.getShop().getState().equals(customer.getState());

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

                totalTaxableValue = totalTaxableValue.add(taxableValue);
                totalGSTAmount = totalGSTAmount.add(gstAmount);
            } else {
                saleItem.setTaxableValue(BigDecimal.ZERO);
                saleItem.setGstType(GSTType.GST_0);
                saleItem.setCgstAmt(BigDecimal.ZERO);
                saleItem.setSgstAmt(BigDecimal.ZERO);
                saleItem.setIgstAmt(BigDecimal.ZERO);
            }

            existing.getSaleItems().add(saleItem);
        }

        // ===== 2) Deduct stock (THIS DID NOT HAPPEN IN DRAFT BEFORE) =====
        for (SaleItemDto itemDto : dto.getItems()) {
            stockService.deductStock(
                    itemDto.getItemVariantId(),
                    itemDto.getQty(),
                    "Sale Transaction",
                    "Sale #" + existing.getInvoiceNo()
            );
        }

        // ===== 3) Calculate final amount =====
        BigDecimal totalBeforeRoundOff =
                Boolean.TRUE.equals(dto.getIsGstRequired())
                        ? totalTaxableValue.add(totalGSTAmount)
                        : dto.getItems().stream()
                        .map(i -> i.getQty()
                                .multiply(i.getUnitPrice())
                                .subtract(i.getDiscount() != null ? i.getDiscount() : BigDecimal.ZERO))
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal finalTotal = totalBeforeRoundOff.setScale(0, RoundingMode.HALF_UP);
        BigDecimal roundOff = totalBeforeRoundOff.subtract(finalTotal);

        existing.setTotalAmount(finalTotal);
        existing.setRoundOff(roundOff);
        existing.setCustomer(customer);

        // ===== 4) Mark as COMPLETED (KEY LINE) =====
        existing.setStatus(SaleStatus.COMPLETED);

        Sale saved = saleRepository.save(existing);

        // ===== 5) Create delivery if present =====
        if (dto.getDelivery() != null) {
            DeliveryDTO deliveryDTO = dto.getDelivery();
            deliveryDTO.setSaleId(saved.getId());
            deliveryDTO.setInvoiceNumber(saved.getInvoiceNo());
            if (customer != null) {
                deliveryDTO.setCustomerName(customer.getName());
            }
            deliveryService.createDelivery(deliveryDTO);
        }

        // ===== 6) Create customer ledger entry =====
        if (customer != null) {
            CustomerLedgerDto ledger = new CustomerLedgerDto();
            ledger.setAmount(finalTotal);
            ledger.setType(CustomerLedgerType.CREDIT);
            ledger.setDescription("Sale #" + saved.getInvoiceNo());
            ledgerService.addEntry(customer.getId(), ledger);
        }

        // ===== 7) Create payments =====
        if (dto.getPaymentDetails() != null) {
            BigDecimal totalPaid = BigDecimal.ZERO;

            for (PaymentDto p : dto.getPaymentDetails()) {
                p.setSourceId(saved.getId());
                p.setSourceType(PaymentSourceType.SALE);
                p.setCustomerId(customer != null ? customer.getId() : null);
                p.setPaymentDate(LocalDateTime.now());

                paymentService.createPayment(p);
                totalPaid = totalPaid.add(p.getAmount());

                if (customer != null) {
                    CustomerLedgerDto payLedger = new CustomerLedgerDto();
                    payLedger.setAmount(p.getAmount());
                    payLedger.setType(CustomerLedgerType.DEBIT);
                    payLedger.setDescription(
                            "Payment for Sale #" + saved.getInvoiceNo()
                                    + " (" + p.getPaymentMethod() + ")"
                    );
                    ledgerService.addEntry(customer.getId(), payLedger);
                }
            }

            if (totalPaid.compareTo(finalTotal) > 0) {
                throw new BusinessValidationException("Total paid cannot exceed sale amount");
            }
        }

        // ===== 8) Generate signed invoice =====
        String signedToken =
                jwtUtil.generateInvoiceToken(saved.getId(), saved.getInvoiceNo());
        String signedUrl = "/api/invoices/signed?token=" + signedToken;

        SaleDto result = mapper.toDto(saved);
        result.setSignedInvoiceUrl(signedUrl);

        return result;
    }

}