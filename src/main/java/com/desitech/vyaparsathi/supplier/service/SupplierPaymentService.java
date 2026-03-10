package com.desitech.vyaparsathi.supplier.service;

import com.desitech.vyaparsathi.common.exception.ResourceNotFoundException;
import com.desitech.vyaparsathi.payment.enums.PaymentStatus;
import com.desitech.vyaparsathi.purchaseorder.dto.PurchaseOrderPaymentSummaryDto;
import com.desitech.vyaparsathi.supplier.dto.SupplierPaymentDto;
import com.desitech.vyaparsathi.supplier.entity.Supplier;
import com.desitech.vyaparsathi.supplier.entity.SupplierPayment;
import com.desitech.vyaparsathi.supplier.enums.SupplierPaymentStatus;
import com.desitech.vyaparsathi.supplier.exception.SupplierPaymentException;
import com.desitech.vyaparsathi.supplier.repository.SupplierPaymentRepository;
import com.desitech.vyaparsathi.supplier.repository.SupplierRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Handles all supplier payment accounting logic.
 * Records payments against purchase orders and tracks outstanding dues.
 */
@Service
public class SupplierPaymentService {

    @Autowired
    private SupplierPaymentRepository supplierPaymentRepository;

    @Autowired
    private SupplierRepository supplierRepository;

    /**
     * Records a payment to a supplier against a purchase order.
     * Validates that the payment amount is positive and does not exceed the amount due.
     *
     * @param purchaseOrderId the ID of the purchase order being paid
     * @param supplierId      the ID of the supplier
     * @param poTotalAmount   the total amount of the purchase order
     * @param dto             payment details
     * @return the persisted SupplierPaymentDto
     */
    @Transactional
    public SupplierPaymentDto recordPayment(Long purchaseOrderId, Long supplierId,
                                            BigDecimal poTotalAmount, SupplierPaymentDto dto) {
        if (dto.getAmount() == null || dto.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new SupplierPaymentException("Payment amount must be positive");
        }

        BigDecimal amountDue = calculateDueAmount(purchaseOrderId, poTotalAmount);
        if (dto.getAmount().compareTo(amountDue) > 0) {
            throw new SupplierPaymentException("Payment amount (" + dto.getAmount() +
                    ") exceeds the amount due (" + amountDue + ")");
        }

        Supplier supplier = supplierRepository.findById(supplierId)
                .orElseThrow(() -> new ResourceNotFoundException("Supplier not found with ID: " + supplierId));

        // Determine the cumulative payment status for this PO after this payment is applied
        BigDecimal remainingDue = amountDue.subtract(dto.getAmount()).max(BigDecimal.ZERO);
        SupplierPaymentStatus status = resolveOverallStatus(remainingDue, poTotalAmount);

        SupplierPayment payment = new SupplierPayment();
        payment.setSupplier(supplier);
        payment.setPurchaseOrderId(purchaseOrderId);
        payment.setAmount(dto.getAmount());
        payment.setPaymentDate(dto.getPaymentDate() != null ? dto.getPaymentDate() : LocalDateTime.now());
        payment.setPaymentMethod(dto.getPaymentMethod());
        payment.setReference(dto.getReference());
        payment.setNotes(dto.getNotes());
        payment.setStatus(status);

        SupplierPayment saved = supplierPaymentRepository.save(payment);
        return toDto(saved);
    }

    /**
     * Returns a paginated list of payments recorded against a purchase order.
     *
     * @param purchaseOrderId the purchase order ID
     * @param pageable        pagination parameters
     */
    public Page<SupplierPaymentDto> getPaymentsByPurchaseOrder(Long purchaseOrderId, Pageable pageable) {
        return supplierPaymentRepository.findByPurchaseOrderId(purchaseOrderId, pageable)
                .map(this::toDto);
    }

    /**
     * Calculates the amount still owed for a purchase order.
     *
     * @param purchaseOrderId the purchase order ID
     * @param totalAmount     the total value of the purchase order
     * @return the remaining due amount (never negative)
     */
    public BigDecimal calculateDueAmount(Long purchaseOrderId, BigDecimal totalAmount) {
        BigDecimal totalPaid = supplierPaymentRepository.sumByPurchaseOrderId(purchaseOrderId);
        BigDecimal due = (totalAmount != null ? totalAmount : BigDecimal.ZERO).subtract(totalPaid);
        return due.max(BigDecimal.ZERO);
    }

    /**
     * Returns the payment summary (total, paid, due, status) for a purchase order.
     *
     * @param purchaseOrderId the purchase order ID
     * @param poNumber        the human-readable PO number
     * @param totalAmount     the total value of the purchase order
     * @param currentStatus   the current payment status on the PO
     * @return payment summary
     */
    public PurchaseOrderPaymentSummaryDto getPaymentSummary(Long purchaseOrderId, String poNumber,
                                                            BigDecimal totalAmount,
                                                            PaymentStatus currentStatus) {
        BigDecimal total = totalAmount != null ? totalAmount : BigDecimal.ZERO;
        BigDecimal amountDue = calculateDueAmount(purchaseOrderId, total);
        BigDecimal totalPaid = total.subtract(amountDue).max(BigDecimal.ZERO);
        return new PurchaseOrderPaymentSummaryDto(purchaseOrderId, poNumber, total, totalPaid, amountDue, currentStatus);
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    /**
     * Determines the overall PO payment status based on the cumulative remaining due
     * after applying the latest payment.
     */
    private SupplierPaymentStatus resolveOverallStatus(BigDecimal remainingDue, BigDecimal totalAmount) {
        if (remainingDue.compareTo(BigDecimal.ZERO) <= 0) {
            return SupplierPaymentStatus.PAID;
        } else if (remainingDue.compareTo(totalAmount) < 0) {
            return SupplierPaymentStatus.PARTIALLY_PAID;
        }
        return SupplierPaymentStatus.PENDING;
    }

    private SupplierPaymentDto toDto(SupplierPayment entity) {
        SupplierPaymentDto dto = new SupplierPaymentDto();
        dto.setId(entity.getId());
        dto.setSupplierId(entity.getSupplier() != null ? entity.getSupplier().getId() : null);
        dto.setPurchaseOrderId(entity.getPurchaseOrderId());
        dto.setAmount(entity.getAmount());
        dto.setPaymentDate(entity.getPaymentDate());
        dto.setPaymentMethod(entity.getPaymentMethod());
        dto.setReference(entity.getReference());
        dto.setNotes(entity.getNotes());
        dto.setStatus(entity.getStatus());
        return dto;
    }
}
