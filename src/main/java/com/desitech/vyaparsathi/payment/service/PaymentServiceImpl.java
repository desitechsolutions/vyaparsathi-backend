package com.desitech.vyaparsathi.payment.service;

import com.desitech.vyaparsathi.common.exception.BusinessValidationException;
import com.desitech.vyaparsathi.customer.dto.CustomerLedgerDto;
import com.desitech.vyaparsathi.customer.entity.CustomerLedgerType;
import com.desitech.vyaparsathi.customer.service.CustomerLedgerService;
import com.desitech.vyaparsathi.payment.dto.BulkPaymentRequest;
import com.desitech.vyaparsathi.payment.dto.PaymentDto;
import com.desitech.vyaparsathi.payment.dto.PaymentReceivedRequest;
import com.desitech.vyaparsathi.payment.entity.Payment;
import com.desitech.vyaparsathi.payment.enums.PaymentMethod;
import com.desitech.vyaparsathi.payment.enums.PaymentSourceType;
import com.desitech.vyaparsathi.payment.enums.PaymentStatus;
import com.desitech.vyaparsathi.payment.repository.PaymentRepository;
import com.desitech.vyaparsathi.payment.mapper.PaymentMapper;
import com.desitech.vyaparsathi.purchaseorder.entity.PurchaseOrder;
import com.desitech.vyaparsathi.purchaseorder.repository.PurchaseOrderRepository;
import com.desitech.vyaparsathi.sales.entity.Sale;
import com.desitech.vyaparsathi.sales.repository.SaleRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.stream.Collectors;

import static java.math.BigDecimal.ZERO;

import com.desitech.vyaparsathi.purchasereturn.entity.PurchaseReturn;
import com.desitech.vyaparsathi.purchasereturn.repository.PurchaseReturnRepository;
import org.springframework.context.annotation.Lazy;

@Service
public class PaymentServiceImpl implements PaymentService {

    private static final Logger logger = LoggerFactory.getLogger(PaymentServiceImpl.class);

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private PaymentMapper paymentMapper;

    @Autowired
    private SaleRepository saleRepository;

    @Autowired
    private PurchaseOrderRepository purchaseOrderRepository;

    @Autowired
    @Lazy
    private PurchaseReturnRepository purchaseReturnRepository;

    @Autowired
    private CustomerLedgerService ledgerService;

    // Methods whose payments are instantly settled
    private static final Set<PaymentMethod> INSTANTLY_SETTLED_METHODS = Set.of(
            PaymentMethod.CASH,
            PaymentMethod.UPI,
            PaymentMethod.CARD
    );

    @Override
    @Transactional
    public PaymentDto createPayment(PaymentDto dto) {
        Payment payment = paymentMapper.toEntity(dto);
        BigDecimal paymentAmount = payment.getAmount() != null ? payment.getAmount() : ZERO;

        // 1. Fetch totals using Optimized Database Sum (No N+1)
        BigDecimal totalAmount = ZERO;
        BigDecimal totalPaidBefore = ZERO;

        if (payment.getSourceId() != null && payment.getSourceType() != null) {
            totalAmount = getTotalAmountForSource(payment.getSourceId(), payment.getSourceType());

            // FIX: Use a single DB query instead of streaming a list
            totalPaidBefore = paymentRepository.sumPaymentsBySource(
                    payment.getSourceType(), payment.getSourceId());
        }

        BigDecimal totalPaidAfter = totalPaidBefore.add(paymentAmount);

        // 2. Validation: Prevent accidental overpayment (if not bulk/advance)
        if (payment.getSourceId() != null && totalPaidAfter.compareTo(totalAmount) > 0) {
            throw new BusinessValidationException("Payment exceeds due amount for this source.");
        }

        // 3. Status Determination
        payment.setStatus(determinePaymentStatus(
                payment.getPaymentMethod(),
                totalPaidAfter,
                totalAmount,
                dto.getStatus(),
                payment.getSourceType(),
                payment.getSourceId()
        ));

        Payment saved = paymentRepository.save(payment);

        logger.info("Payment created: id={}, sourceType={}, sourceId={}, amount={}, status={}",
                saved.getId(), saved.getSourceType(), saved.getSourceId(), saved.getAmount(), saved.getStatus());
        // 4. Enhanced Ledger Entry
        if (saved.getCustomerId() != null && paymentAmount.compareTo(ZERO) > 0) {
            CustomerLedgerDto ledgerDto = new CustomerLedgerDto();
            ledgerDto.setAmount(paymentAmount);
            ledgerDto.setType(CustomerLedgerType.DEBIT);

            // FIX: Add more context to description for the customer statement
            String description = "Payment for " + payment.getSourceType() + " #" + payment.getSourceId();
            if (saved.getReference() != null) description += " (Ref: " + saved.getReference() + ")";

            ledgerDto.setDescription(description);
            ledgerService.addEntry(saved.getCustomerId(), ledgerDto);
        }

        // 5. Sync Source Status (Sale/PO)
        if (payment.getSourceType() != null && payment.getSourceId() != null) {
            updateSourcePaymentStatus(payment.getSourceType(), payment.getSourceId());
        }

        return paymentMapper.toDto(saved);
    }

    /**
     * Determines the payment status using the payment method, total paid, total due, and explicit status (if present).
     * Now considers all payment methods for the given sourceId.
     */
    private PaymentStatus determinePaymentStatus(
            PaymentMethod method,
            BigDecimal totalPaid,
            BigDecimal totalAmount,
            PaymentStatus explicitStatus,
            PaymentSourceType sourceType,
            Long sourceId
    ) {
        if (explicitStatus != null) return explicitStatus;
        if (method == null) return PaymentStatus.PENDING;

        // Fetch all payment methods for this source
        Set<PaymentMethod> allMethods = (sourceType != null && sourceId != null)
                ? paymentRepository.findPaymentMethodsBySource(sourceType, sourceId)
                : new HashSet<>();

        allMethods.add(method);

        boolean hasOnlyInstantlySettled = allMethods.stream().allMatch(INSTANTLY_SETTLED_METHODS::contains);
        boolean hasPendingMethods = allMethods.stream().anyMatch(m ->
                m == PaymentMethod.CHEQUE || m == PaymentMethod.NET_BANKING || m == PaymentMethod.OTHER);

        if (totalAmount.compareTo(ZERO) <= 0) {
            return PaymentStatus.PENDING;
        }

        if (totalPaid.compareTo(ZERO) == 0) {
            return PaymentStatus.PENDING;
        } else if (totalPaid.compareTo(totalAmount) < 0) {
            return PaymentStatus.PARTIALLY_PAID;
        } else if (totalPaid.compareTo(totalAmount) >= 0) {
            // Fully paid, but check if all methods are instantly settled
            if (hasOnlyInstantlySettled) {
                return PaymentStatus.PAID;
            } else if (hasPendingMethods) {
                return PaymentStatus.PARTIALLY_PAID;
            } else {
                return PaymentStatus.PAID;
            }
        }
        return PaymentStatus.PENDING;
    }

    @Override
    @Cacheable("paymentsBySource")
    public Page<PaymentDto> getPaymentsBySource(PaymentSourceType sourceType, Long sourceId, Pageable pageable) {
        return paymentRepository
                .findBySourceTypeAndSourceId(sourceType, sourceId, pageable)
                .map(this::enrichPaymentDto);
    }

    @Override
    @Cacheable("paymentsBySupplier")
    public Page<PaymentDto> getPaymentsBySupplier(Long supplierId, Pageable pageable) {
        return paymentRepository
                .findBySupplierId(supplierId, pageable)
                .map(this::enrichPaymentDto);
    }
    @Override
    @Cacheable("paymentsByCustomer")
    public Page<PaymentDto> getPaymentsByCustomer(Long customerId, Pageable pageable) {
        Page<Payment> payments = paymentRepository.findByCustomerId(customerId, pageable);
        return payments.map(this::enrichPaymentDto);
    }
    @Override
    public Optional<PaymentDto> getPayment(Long id) {
        return paymentRepository.findById(id)
                .map(this::enrichPaymentDto);
    }

    @Override
    @Transactional
    public void bulkPayment(BulkPaymentRequest request) {
        BigDecimal totalToProcess = request.getTotalAmount();
        BigDecimal remainingToAllocate = totalToProcess;
        Long customerId = request.getCustomerId();

        // 1. To track for the Ledger Description
        List<String> paidInvoices = new ArrayList<>();

        // 2. Fetch Sales (Waterfall or Selection)
        List<Sale> targetSales;
        if (request.getSelectedSaleIds() != null && !request.getSelectedSaleIds().isEmpty()) {
            targetSales = saleRepository.findAllById(request.getSelectedSaleIds());
            targetSales.sort(Comparator.comparing(Sale::getId));
        } else {
            targetSales = saleRepository.findByCustomerIdAndPaymentStatusInOrderByIdAsc(
                    customerId, List.of(PaymentStatus.PENDING, PaymentStatus.PARTIALLY_PAID));
        }

        // 3. Process Payments
        if (!targetSales.isEmpty()) {
            Set<Long> saleIds = targetSales.stream().map(Sale::getId).collect(Collectors.toSet());
            Map<Long, BigDecimal> paidAmountsMap = getTotalPaidBySaleIds(saleIds);

            for (Sale sale : targetSales) {
                if (remainingToAllocate.compareTo(ZERO) <= 0) break;

                BigDecimal totalSaleAmount = sale.getTotalAmount() != null ? sale.getTotalAmount() : ZERO;
                BigDecimal alreadyPaid = paidAmountsMap.getOrDefault(sale.getId(), ZERO);
                BigDecimal currentDue = totalSaleAmount.subtract(alreadyPaid).max(ZERO);

                if (currentDue.compareTo(ZERO) > 0) {
                    BigDecimal allocation = remainingToAllocate.min(currentDue);

                    // Track invoice for description
                    paidInvoices.add(sale.getInvoiceNo() != null ? sale.getInvoiceNo() : "#" + sale.getId());

                    Payment internalPayment = new Payment();
                    internalPayment.setSourceId(sale.getId());
                    internalPayment.setSourceType(PaymentSourceType.SALE);
                    internalPayment.setAmount(allocation);
                    internalPayment.setCustomerId(customerId);
                    internalPayment.setPaymentMethod(request.getPaymentMethod());
                    internalPayment.setPaymentDate(request.getPaymentDate());
                    internalPayment.setStatus(determinePaymentStatus(request.getPaymentMethod(), alreadyPaid.add(allocation), totalSaleAmount, null, PaymentSourceType.SALE, sale.getId()));

                    paymentRepository.save(internalPayment);
                    updateSourcePaymentStatus(PaymentSourceType.SALE, sale.getId());

                    remainingToAllocate = remainingToAllocate.subtract(allocation);
                }
            }
        }

        // 4. Construct Smart Description
        StringBuilder description = new StringBuilder("Bulk Payment [" + request.getPaymentMethod() + "]");

        if (!paidInvoices.isEmpty()) {
            description.append(" for Invoices: ").append(String.join(", ", paidInvoices));
        }

        if (remainingToAllocate.compareTo(ZERO) > 0) {
            description.append(" (Includes ₹").append(remainingToAllocate).append(" as Advance)");
            saveAdvancePayment(customerId, remainingToAllocate, request);
        }

        // 5. Record SINGLE Ledger Entry with rich context
        CustomerLedgerDto ledgerDto = new CustomerLedgerDto();
        ledgerDto.setAmount(totalToProcess);
        ledgerDto.setType(CustomerLedgerType.DEBIT); // DEBIT = Payment Received
        ledgerDto.setDescription(description.toString());
        ledgerService.addEntry(customerId, ledgerDto);
    }
    private void saveAdvancePayment(Long customerId, BigDecimal amount, BulkPaymentRequest request) {
        Payment advancePayment = new Payment();
        advancePayment.setCustomerId(customerId);
        advancePayment.setAmount(amount);
        advancePayment.setPaymentMethod(request.getPaymentMethod());
        advancePayment.setPaymentDate(request.getPaymentDate());
        advancePayment.setReference(request.getReference());
        advancePayment.setSourceType(null); // No specific sale
        advancePayment.setSourceId(null);   // No specific sale ID

        // Advance is considered "Paid" as the money is already received
        advancePayment.setStatus(PaymentStatus.PAID);
        advancePayment.setNotes("Unallocated excess from bulk payment");

        paymentRepository.save(advancePayment);
        logger.info("Recorded Advance Payment: {} for Customer ID: {}", amount, customerId);
    }
    private BigDecimal calculateTotalPaidForSource(Long sourceId, PaymentSourceType sourceType) {
        BigDecimal total = paymentRepository.sumPaymentsBySource(sourceType, sourceId);
        return total != null ? total : ZERO;
    }

    public BigDecimal getCustomerAdvanceBalance(Long customerId) {
        BigDecimal advance = paymentRepository.getUnallocatedCreditByCustomerId(customerId);
        return advance != null ? advance : BigDecimal.ZERO;
    }
    /**
     * Record a due/partial payment for any source (SALE, PURCHASE_ORDER, etc.)
     */
    @Override
    @Transactional
    public PaymentDto recordDuePayment(PaymentReceivedRequest request) {
        if (request.getAmount() == null || request.getAmount().compareTo(ZERO) <= 0) {
            throw new IllegalArgumentException("Invalid payment amount");
        }
        if (request.getSourceId() == null || request.getSourceType() == null) {
            throw new IllegalArgumentException("Source ID and type are required");
        }

        // Get previous payments for this source
        BigDecimal totalPaidBefore = paymentRepository.sumPaymentsBySource(request.getSourceType(), request.getSourceId());
        if (totalPaidBefore == null) totalPaidBefore = ZERO;

        BigDecimal totalAmount = getTotalAmountForSource(request.getSourceId(), request.getSourceType());
        BigDecimal totalDue = totalAmount.subtract(totalPaidBefore).max(ZERO);

        if (request.getAmount().compareTo(totalDue) > 0) {
            throw new BusinessValidationException("Payment amount exceeds due amount of " + totalDue);
        }

        // Create and populate Payment entity
        Payment payment = paymentMapper.toEntityFromPayRequest(request);

        // Calculate new total paid
        BigDecimal totalPaidAfter = totalPaidBefore.add(request.getAmount());
        payment.setStatus(determinePaymentStatus(
                payment.getPaymentMethod(),
                totalPaidAfter,
                totalAmount,
                null,
                payment.getSourceType(),
                payment.getSourceId()
        ));
        // Record as DEBIT in ledger to reduce debt
        CustomerLedgerDto ledgerDto = new CustomerLedgerDto();
        ledgerDto.setAmount(request.getAmount());
        ledgerDto.setType(CustomerLedgerType.DEBIT);
        ledgerDto.setDescription("Due Payment for Sale #" + request.getSourceId() + " (" + request.getPaymentMethod() + ")");
        ledgerService.addEntry(request.getCustomerId(), ledgerDto);

        Payment saved = paymentRepository.save(payment);
        logger.info("Due payment recorded: id={}, sourceType={}, sourceId={}, amount={}, status={}",
                saved.getId(), saved.getSourceType(), saved.getSourceId(), saved.getAmount(), saved.getStatus());

        // After saving, update parent source payment status (sale/purchase/etc.)
        updateSourcePaymentStatus(payment.getSourceType(), payment.getSourceId());

        return paymentMapper.toDto(saved);
    }

    @Override
    public BigDecimal calculateDueAmount(Long sourceId, PaymentSourceType sourceType, BigDecimal totalAmount) {
        BigDecimal totalPaid = paymentRepository.sumPaymentsBySource(sourceType, sourceId);
        return totalAmount.subtract(totalPaid).max(ZERO);
    }

    public BigDecimal getTotalAmountForSource(Long sourceId, PaymentSourceType sourceType) {
        if (PaymentSourceType.SALE.equals(sourceType)) {
            Sale sale = saleRepository.findById(sourceId)
                    .orElseThrow(() -> new EntityNotFoundException("Sale not found"));
            if (sale.getTotalAmount() == null) {
                logger.warn("Total amount missing for Sale with id={}", sourceId);
                return ZERO;
            }
            return sale.getTotalAmount();
        } else if (PaymentSourceType.PURCHASE_ORDER.equals(sourceType)) {
            PurchaseOrder po = purchaseOrderRepository.findById(sourceId)
                    .orElseThrow(() -> new EntityNotFoundException("Purchase Order not found"));
            if (po.getTotalAmount() == null) {
                logger.warn("Total amount missing for PurchaseOrder with id={}", sourceId);
                return ZERO;
            }
            return po.getTotalAmount();
        }
        throw new IllegalArgumentException("Unsupported source type: " + sourceType);
    }

    /**
     * Update parent payment status (Sale/PurchaseOrder/other types) based on all payments and payment methods.
     */
    private void updateSourcePaymentStatus(PaymentSourceType sourceType, Long sourceId) {
        BigDecimal totalPaid = paymentRepository.sumPaymentsBySource(sourceType, sourceId);
        if (totalPaid == null) totalPaid = BigDecimal.ZERO;

        BigDecimal totalAmount = getTotalAmountForSource(sourceId, sourceType);
        if (totalAmount == null || totalAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        Set<PaymentMethod> allMethods = paymentRepository.findPaymentMethodsBySource(sourceType, sourceId);

        boolean hasOnlyInstantlySettled = !allMethods.isEmpty() && allMethods.stream().allMatch(INSTANTLY_SETTLED_METHODS::contains);
        boolean hasPendingMethods = allMethods.stream().anyMatch(m ->
                m == PaymentMethod.CHEQUE || m == PaymentMethod.NET_BANKING || m == PaymentMethod.OTHER);

        PaymentStatus status;
        if (totalPaid.compareTo(ZERO) == 0) {
            status = PaymentStatus.PENDING;
        } else if (totalPaid.compareTo(totalAmount) < 0) {
            status = PaymentStatus.PARTIALLY_PAID;
        } else if (totalPaid.compareTo(totalAmount) >= 0) {
            if (hasOnlyInstantlySettled) {
                status = PaymentStatus.PAID;
            } else if (hasPendingMethods) {
                status = PaymentStatus.PARTIALLY_PAID;
            } else {
                status = PaymentStatus.PAID;
            }
        } else {
            status = PaymentStatus.PENDING;
        }

        switch (sourceType) {
            case SALE -> {
                Sale sale = saleRepository.findById(sourceId)
                        .orElseThrow(() -> new EntityNotFoundException("Sale not found"));
                sale.setPaymentStatus(status);
                saleRepository.save(sale);
            }
            case PURCHASE_ORDER -> {
                PurchaseOrder po = purchaseOrderRepository.findById(sourceId)
                        .orElseThrow(() -> new EntityNotFoundException("Purchase order not found"));
                po.setPaymentStatus(status);
                purchaseOrderRepository.save(po);
            }
            default -> throw new UnsupportedOperationException("Unsupported sourceType: " + sourceType);
        }
    }
    private PaymentDto enrichPaymentDto(Payment payment) {
        PaymentDto dto = paymentMapper.toDto(payment);

        if (payment.getSourceType() == PaymentSourceType.SALE) {
            saleRepository.findById(payment.getSourceId())
                    .map(Sale::getInvoiceNo)
                    .ifPresent(dto::setInvoiceNumber);
        }

        return dto;
    }

    /**
     * Returns total paid amount per sale ID
     * @param saleIds Set of sale IDs (can be null or empty)
     * @return Map<saleId, totalPaidAmount> — never null
     */
    public Map<Long, BigDecimal> getTotalPaidBySaleIds(Set<Long> saleIds) {
        if (saleIds == null || saleIds.isEmpty()) {
            return Collections.emptyMap();
        }

        List<Object[]> sums = paymentRepository.sumPaymentsBySaleIds(saleIds, PaymentSourceType.SALE);

        Map<Long, BigDecimal> paidMap = new HashMap<>();
        for (Object[] row : sums) {
            Long saleId = (Long) row[0];
            BigDecimal sum = row[1] != null ? (BigDecimal) row[1] : ZERO;
            paidMap.put(saleId, sum);
        }

        // Fill missing sales with ZERO
        saleIds.forEach(id -> paidMap.putIfAbsent(id, ZERO));

        return paidMap;
    }

    @Override
    @Transactional
    public BigDecimal applyAdvanceToSale(Long customerId, Long saleId, BigDecimal saleTotal) {
        BigDecimal remainingSaleDue = saleTotal;
        BigDecimal totalApplied = BigDecimal.ZERO;

        // 1. Get all payments where sourceId is NULL (Advance/Unallocated)
        List<Payment> advances = paymentRepository.findAvailableAdvances(customerId);

        for (Payment advance : advances) {
            if (remainingSaleDue.compareTo(BigDecimal.ZERO) <= 0) break;

            BigDecimal advanceAmount = advance.getAmount();
            // Determine how much of THIS specific advance to use
            BigDecimal amountToTake = advanceAmount.min(remainingSaleDue);

            // 2. Reduce the original Advance record
            advance.setAmount(advanceAmount.subtract(amountToTake));
            // If the advance is now 0, it's fully consumed
            paymentRepository.save(advance);

            // 3. Create a NEW Payment record linked to this Sale
            Payment linkedPayment = new Payment();
            linkedPayment.setCustomerId(customerId);
            linkedPayment.setSourceId(saleId);
            linkedPayment.setSourceType(PaymentSourceType.SALE);
            linkedPayment.setAmount(amountToTake);
            linkedPayment.setPaymentMethod(advance.getPaymentMethod());
            linkedPayment.setPaymentDate(LocalDateTime.now());
            linkedPayment.setNotes("Settled from Advance (Ref: " + advance.getId() + ")");
            linkedPayment.setStatus(PaymentStatus.PAID);
            paymentRepository.save(linkedPayment);

            remainingSaleDue = remainingSaleDue.subtract(amountToTake);
            totalApplied = totalApplied.add(amountToTake);
        }

        // 4. Update Sale status (Paid/Partially Paid)
        if (totalApplied.compareTo(BigDecimal.ZERO) > 0) {
            updateSourcePaymentStatus(PaymentSourceType.SALE, saleId);
        }

        return totalApplied;
    }

/*    @Transactional
    public void processCashRefund(Long customerId, BigDecimal amount, String reason) {
        // 1. Verify they have enough 'Advance' to refund
        BigDecimal currentAdvance = customerService.getCustomerAdvanceBalance(customerId);
        if (amount.compareTo(currentAdvance) > 0) {
            throw new BusinessValidationException("Refund amount exceeds available customer credit.");
        }

        // 2. Create a Negative Payment (The Payout)
        Payment refundPayout = new Payment();
        refundPayout.setCustomerId(customerId);
        refundPayout.setAmount(amount.negate()); // IMPORTANT: Negative amount reduces the balance
        refundPayout.setPaymentMethod(PaymentMethod.CASH); // Or however you paid them back
        refundPayout.setPaymentDate(LocalDateTime.now());
        refundPayout.setReference("CASH_REFUND: " + reason);
        refundPayout.setSourceType(PaymentSourceType.ADVANCE); // Mark as advance withdrawal
        paymentRepository.save(refundPayout);

        // 3. Record in Ledger
        CustomerLedgerDto ledgerDto = new CustomerLedgerDto();
        ledgerDto.setAmount(amount);
        ledgerDto.setType(CustomerLedgerType.CREDIT); // Or a new type 'REFUND_PAYOUT'
        ledgerDto.setDescription("Cash Refund Paid to Customer: " + reason);
        ledgerService.addEntry(customerId, ledgerDto);
    }*/
}