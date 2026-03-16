package com.desitech.vyaparsathi.supplier.service;

import com.desitech.vyaparsathi.common.exception.ResourceNotFoundException;
import com.desitech.vyaparsathi.payment.dto.PaymentDto;
import com.desitech.vyaparsathi.payment.enums.PaymentSourceType;
import com.desitech.vyaparsathi.payment.service.PaymentService;
import com.desitech.vyaparsathi.purchaseorder.dto.PurchaseOrderPaymentSummaryDto;
import com.desitech.vyaparsathi.purchaseorder.entity.PurchaseOrder;
import com.desitech.vyaparsathi.purchaseorder.repository.PurchaseOrderRepository;
import com.desitech.vyaparsathi.supplier.dto.SupplierBulkPaymentRequest;
import com.desitech.vyaparsathi.supplier.dto.SupplierPaymentDto;
import com.desitech.vyaparsathi.supplier.exception.SupplierPaymentException;
import com.desitech.vyaparsathi.supplier.repository.SupplierRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Supplier-domain service for all payment operations against Purchase Orders.
 * Delegates persistence to the unified {@link PaymentService} so that all payment
 * records live in the shared {@code payment} table.
 */
@Service
public class SupplierPaymentService {

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private PurchaseOrderRepository purchaseOrderRepository;

    @Autowired
    private SupplierRepository supplierRepository;

    /**
     * Records a single payment against a purchase order.
     * The PO's {@code paymentStatus} is automatically updated by the underlying
     * {@link PaymentService}.
     *
     * @param dto must contain {@code purchaseOrderId}, {@code supplierId}, and {@code amount}
     * @return the persisted PaymentDto from the unified payment table
     */
    @Transactional
    public PaymentDto recordPayment(SupplierPaymentDto dto) {
        if (dto.getAmount() == null || dto.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new SupplierPaymentException("Payment amount must be positive");
        }

        PurchaseOrder po = purchaseOrderRepository.findById(dto.getPurchaseOrderId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Purchase Order not found with ID: " + dto.getPurchaseOrderId()));

        if (!po.getSupplier().getId().equals(dto.getSupplierId())) {
            throw new SupplierPaymentException("Purchase Order " + dto.getPurchaseOrderId() +
                    " does not belong to supplier " + dto.getSupplierId());
        }

        if (!supplierRepository.existsById(dto.getSupplierId())) {
            throw new ResourceNotFoundException("Supplier not found with ID: " + dto.getSupplierId());
        }

        PaymentDto paymentDto = toPaymentDto(dto, po.getId(), dto.getSupplierId());
        return paymentService.createPayment(paymentDto);
    }

    /**
     * Distributes a total payment amount across multiple purchase orders for a supplier
     * in ascending-ID (FIFO) order. The allocation stops when either all selected POs
     * are fully settled or the total amount is exhausted.
     *
     * @param request contains {@code supplierId}, {@code selectedPoIds}, {@code totalAmount}, etc.
     * @return list of persisted PaymentDtos, one per PO that received an allocation
     */
    @Transactional
    public List<PaymentDto> recordBulkPayment(SupplierBulkPaymentRequest request) {
        if (request.getTotalAmount() == null || request.getTotalAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new SupplierPaymentException("Total payment amount must be positive");
        }
        if (request.getSelectedPoIds() == null || request.getSelectedPoIds().isEmpty()) {
            throw new SupplierPaymentException("At least one Purchase Order must be selected");
        }

        if (!supplierRepository.existsById(request.getSupplierId())) {
            throw new ResourceNotFoundException("Supplier not found with ID: " + request.getSupplierId());
        }

        List<PurchaseOrder> pos = purchaseOrderRepository.findAllById(request.getSelectedPoIds());
        // Validate all POs belong to the supplier and sort FIFO
        for (PurchaseOrder po : pos) {
            if (!po.getSupplier().getId().equals(request.getSupplierId())) {
                throw new SupplierPaymentException("Purchase Order " + po.getId() +
                        " does not belong to supplier " + request.getSupplierId());
            }
        }
        pos.sort(Comparator.comparing(PurchaseOrder::getId));

        BigDecimal remaining = request.getTotalAmount();
        List<PaymentDto> results = new ArrayList<>();

        for (PurchaseOrder po : pos) {
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) break;

            BigDecimal due = paymentService.calculateDueAmount(
                    po.getId(), PaymentSourceType.PURCHASE_ORDER, po.getTotalAmount());
            if (due.compareTo(BigDecimal.ZERO) <= 0) continue;

            BigDecimal allocation = remaining.min(due);

            SupplierPaymentDto singleDto = new SupplierPaymentDto();
            singleDto.setSupplierId(request.getSupplierId());
            singleDto.setPurchaseOrderId(po.getId());
            singleDto.setAmount(allocation);
            singleDto.setPaymentDate(request.getPaymentDate());
            singleDto.setPaymentMethod(request.getPaymentMethod());
            singleDto.setReference(request.getReference());
            singleDto.setNotes(request.getNotes());

            results.add(paymentService.createPayment(
                    toPaymentDto(singleDto, po.getId(), request.getSupplierId())));
            remaining = remaining.subtract(allocation);
        }

        return results;
    }

    /**
     * Returns a paginated list of payments recorded against a specific purchase order.
     */
    public Page<PaymentDto> getPaymentsByPurchaseOrder(Long poId, Pageable pageable) {
        if (!purchaseOrderRepository.existsById(poId)) {
            throw new ResourceNotFoundException("Purchase Order not found with ID: " + poId);
        }
        return paymentService.getPaymentsBySource(PaymentSourceType.PURCHASE_ORDER, poId, pageable);
    }

    /**
     * Returns a paginated list of all payments associated with a supplier.
     */
    public Page<PaymentDto> getPaymentsBySupplier(Long supplierId, Pageable pageable) {
        if (!supplierRepository.existsById(supplierId)) {
            throw new ResourceNotFoundException("Supplier not found with ID: " + supplierId);
        }
        return paymentService.getPaymentsBySupplier(supplierId, pageable);
    }

    /**
     * Returns the payment summary (total, paid, due, status) for a purchase order.
     */
    public PurchaseOrderPaymentSummaryDto getPaymentSummary(Long poId) {
        PurchaseOrder po = purchaseOrderRepository.findById(poId)
                .orElseThrow(() -> new ResourceNotFoundException("Purchase Order not found with ID: " + poId));
        BigDecimal total = po.getTotalAmount() != null ? po.getTotalAmount() : BigDecimal.ZERO;
        BigDecimal due = paymentService.calculateDueAmount(poId, PaymentSourceType.PURCHASE_ORDER, total);
        BigDecimal paid = total.subtract(due).max(BigDecimal.ZERO);
        return new PurchaseOrderPaymentSummaryDto(poId, po.getPoNumber(), total, paid, due, po.getPaymentStatus());
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

    private PaymentDto toPaymentDto(SupplierPaymentDto src, Long poId, Long supplierId) {
        PaymentDto dto = new PaymentDto();
        dto.setSourceType(PaymentSourceType.PURCHASE_ORDER);
        dto.setSourceId(poId);
        dto.setSupplierId(supplierId);
        dto.setAmount(src.getAmount());
        dto.setPaymentDate(src.getPaymentDate() != null ? src.getPaymentDate() : LocalDateTime.now());
        dto.setPaymentMethod(src.getPaymentMethod());
        dto.setReference(src.getReference());
        dto.setNotes(src.getNotes());
        return dto;
    }
}

