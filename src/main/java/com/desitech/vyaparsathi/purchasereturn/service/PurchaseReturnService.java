package com.desitech.vyaparsathi.purchasereturn.service;

import com.desitech.vyaparsathi.common.exception.BusinessValidationException;
import com.desitech.vyaparsathi.common.exception.ResourceNotFoundException;
import com.desitech.vyaparsathi.common.util.TenantUtils;
import com.desitech.vyaparsathi.inventory.entity.ItemVariant;
import com.desitech.vyaparsathi.inventory.entity.StockMovement;
import com.desitech.vyaparsathi.inventory.enums.StockMovementType;
import com.desitech.vyaparsathi.inventory.repository.ItemVariantRepository;
import com.desitech.vyaparsathi.inventory.repository.StockMovementRepository;
import com.desitech.vyaparsathi.payment.dto.PaymentDto;
import com.desitech.vyaparsathi.payment.enums.PaymentMethod;
import com.desitech.vyaparsathi.payment.enums.PaymentSourceType;
import com.desitech.vyaparsathi.payment.enums.PaymentStatus;
import com.desitech.vyaparsathi.payment.service.PaymentService;
import com.desitech.vyaparsathi.purchaseorder.entity.PurchaseOrder;
import com.desitech.vyaparsathi.purchaseorder.repository.PurchaseOrderRepository;
import com.desitech.vyaparsathi.purchasereturn.dto.CreatePurchaseReturnDto;
import com.desitech.vyaparsathi.purchasereturn.dto.CreatePurchaseReturnItemDto;
import com.desitech.vyaparsathi.purchasereturn.dto.PurchaseReturnDto;
import com.desitech.vyaparsathi.purchasereturn.entity.PurchaseReturn;
import com.desitech.vyaparsathi.purchasereturn.entity.PurchaseReturnItem;
import com.desitech.vyaparsathi.purchasereturn.enums.PurchaseReturnStatus;
import com.desitech.vyaparsathi.purchasereturn.mapper.PurchaseReturnMapper;
import com.desitech.vyaparsathi.purchasereturn.repository.PurchaseReturnItemRepository;
import com.desitech.vyaparsathi.purchasereturn.repository.PurchaseReturnRepository;
import com.desitech.vyaparsathi.receiving.entity.Receiving;
import com.desitech.vyaparsathi.receiving.entity.ReceivingItem;
import com.desitech.vyaparsathi.receiving.repository.ReceivingRepository;
import com.desitech.vyaparsathi.supplier.entity.Supplier;
import com.desitech.vyaparsathi.supplier.repository.SupplierRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
public class PurchaseReturnService {

    private static final Logger logger = LoggerFactory.getLogger(PurchaseReturnService.class);

    @Autowired
    private PurchaseReturnRepository purchaseReturnRepository;

    @Autowired
    private PurchaseReturnItemRepository purchaseReturnItemRepository;

    @Autowired
    private SupplierRepository supplierRepository;

    @Autowired
    private PurchaseOrderRepository purchaseOrderRepository;

    @Autowired
    private ReceivingRepository receivingRepository;

    @Autowired
    private ItemVariantRepository itemVariantRepository;

    @Autowired
    private StockMovementRepository stockMovementRepository;

    @Autowired
    private com.desitech.vyaparsathi.inventory.service.StockService stockService;

    @Autowired
    private PurchaseReturnMapper purchaseReturnMapper;

    @Transactional
    public PurchaseReturnDto createPurchaseReturn(CreatePurchaseReturnDto dto) {
        Supplier supplier = supplierRepository.findById(dto.getSupplierId())
                .orElseThrow(() -> new ResourceNotFoundException("Supplier not found with ID: " + dto.getSupplierId()));

        PurchaseOrder po = null;
        if (dto.getPurchaseOrderId() != null) {
            po = purchaseOrderRepository.findById(dto.getPurchaseOrderId())
                    .orElseThrow(() -> new ResourceNotFoundException("Purchase Order not found with ID: " + dto.getPurchaseOrderId()));
        }

        Receiving receiving = null;
        if (dto.getReceivingId() != null) {
            receiving = receivingRepository.findById(dto.getReceivingId())
                    .orElseThrow(() -> new ResourceNotFoundException("Receiving not found with ID: " + dto.getReceivingId()));
        }

        PurchaseReturn purchaseReturn = new PurchaseReturn();
        purchaseReturn.setReturnNo(generateReturnNo());
        purchaseReturn.setSupplier(supplier);
        purchaseReturn.setPurchaseOrder(po);
        purchaseReturn.setReceiving(receiving);
        purchaseReturn.setReturnDate(dto.getReturnDate() != null ? dto.getReturnDate() : LocalDateTime.now());
        purchaseReturn.setNotes(dto.getNotes());
        purchaseReturn.setStatus(PurchaseReturnStatus.DRAFT);

        BigDecimal totalAmount = BigDecimal.ZERO;
        Long shopId = TenantUtils.getCurrentShopId();

        for (CreatePurchaseReturnItemDto itemDto : dto.getItems()) {
            ItemVariant variant = itemVariantRepository.findById(itemDto.getItemVariantId())
                    .orElseThrow(() -> new ResourceNotFoundException("Item variant not found with ID: " + itemDto.getItemVariantId()));

            // Batch-level quantity validation if receiving is linked
            if (receiving != null) {
                validateReturnQuantityAgainstReceiving(receiving, variant, itemDto, shopId);
            }

            BigDecimal itemTotalCost = itemDto.getUnitCost().multiply(BigDecimal.valueOf(itemDto.getQuantity()));
            totalAmount = totalAmount.add(itemTotalCost);

            PurchaseReturnItem item = new PurchaseReturnItem();
            item.setPurchaseReturn(purchaseReturn);
            item.setItemVariant(variant);
            item.setBatchNumber(itemDto.getBatchNumber());
            item.setQuantity(itemDto.getQuantity());
            item.setUnitCost(itemDto.getUnitCost());
            item.setTotalCost(itemTotalCost);
            item.setReason(itemDto.getReason());

            purchaseReturn.getItems().add(item);
        }

        purchaseReturn.setTotalAmount(totalAmount);
        PurchaseReturn saved = purchaseReturnRepository.save(purchaseReturn);
        logger.info("Created Purchase Return {} (ID: {}) in DRAFT status", saved.getReturnNo(), saved.getId());
        return purchaseReturnMapper.toDto(saved);
    }

    @Transactional
    public PurchaseReturnDto approvePurchaseReturn(Long id) {
        PurchaseReturn purchaseReturn = purchaseReturnRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Purchase Return not found with ID: " + id));

        if (PurchaseReturnStatus.APPROVED.equals(purchaseReturn.getStatus())) {
            throw new BusinessValidationException("Purchase Return " + purchaseReturn.getReturnNo() + " is already approved.");
        }
        if (PurchaseReturnStatus.CANCELLED.equals(purchaseReturn.getStatus())) {
            throw new BusinessValidationException("Cannot approve a cancelled Purchase Return.");
        }

        // 1. Validate Stock Availability & Deduct Stock via StockMovement
        for (PurchaseReturnItem item : purchaseReturn.getItems()) {
            BigDecimal reqQty = BigDecimal.valueOf(item.getQuantity());
            BigDecimal currentAvailable = stockService.getCurrentStock(item.getItemVariant().getId());
            if (currentAvailable.compareTo(reqQty) < 0) {
                throw new BusinessValidationException(
                        "Insufficient inventory to approve return for SKU " + item.getItemVariant().getSku() +
                        ". Requested: " + reqQty + ", Available in stock: " + currentAvailable
                );
            }

            StockMovement movement = new StockMovement();
            movement.setItemVariant(item.getItemVariant());
            movement.setMovementType(StockMovementType.PURCHASE_RETURN);
            movement.setQuantity(BigDecimal.valueOf(item.getQuantity()).negate());
            movement.setCostPerUnit(item.getUnitCost());
            movement.setBatch(item.getBatchNumber());
            movement.setReason("Purchase Return " + purchaseReturn.getReturnNo() + ": " + (item.getReason() != null ? item.getReason() : "Return to Vendor"));
            movement.setReference(purchaseReturn.getReturnNo());
            movement.setTimestamp(LocalDateTime.now());
            stockMovementRepository.save(movement);
        }

        purchaseReturn.setStatus(PurchaseReturnStatus.APPROVED);
        PurchaseReturn updated = purchaseReturnRepository.save(purchaseReturn);
        logger.info("Approved Purchase Return {} (ID: {}) - Stock deducted", updated.getReturnNo(), updated.getId());

        return purchaseReturnMapper.toDto(updated);
    }

    @Transactional
    public PurchaseReturnDto cancelPurchaseReturn(Long id) {
        PurchaseReturn purchaseReturn = purchaseReturnRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Purchase Return not found with ID: " + id));

        if (PurchaseReturnStatus.APPROVED.equals(purchaseReturn.getStatus())) {
            throw new BusinessValidationException("Cannot cancel an already approved Purchase Return.");
        }
        purchaseReturn.setStatus(PurchaseReturnStatus.CANCELLED);
        PurchaseReturn updated = purchaseReturnRepository.save(purchaseReturn);
        logger.info("Cancelled Purchase Return {}", updated.getReturnNo());
        return purchaseReturnMapper.toDto(updated);
    }

    public PurchaseReturnDto getPurchaseReturnById(Long id) {
        PurchaseReturn purchaseReturn = purchaseReturnRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Purchase Return not found with ID: " + id));
        return purchaseReturnMapper.toDto(purchaseReturn);
    }

    public Page<PurchaseReturnDto> getPurchaseReturns(Long supplierId, Pageable pageable) {
        Long shopId = TenantUtils.getCurrentShopId();
        if (supplierId != null) {
            return purchaseReturnRepository.findBySupplierIdAndShopId(supplierId, shopId, pageable)
                    .map(purchaseReturnMapper::toDto);
        }
        return purchaseReturnRepository.findByShopId(shopId, pageable)
                .map(purchaseReturnMapper::toDto);
    }

    private void validateReturnQuantityAgainstReceiving(Receiving receiving, ItemVariant variant, CreatePurchaseReturnItemDto itemDto, Long shopId) {
        String reqBatch = (itemDto.getBatchNumber() != null && !itemDto.getBatchNumber().isBlank()) ? itemDto.getBatchNumber().trim() : null;

        int originallyReceived = receiving.getItems().stream()
                .filter(ri -> ri.getPurchaseOrderItem().getItemVariant().getId().equals(variant.getId()))
                .filter(ri -> reqBatch == null || reqBatch.equalsIgnoreCase(ri.getBatchNumber()))
                .mapToInt(ri -> ri.getReceivedQty() != null ? ri.getReceivedQty() : 0)
                .sum();

        if (originallyReceived <= 0) {
            throw new BusinessValidationException(
                    "Item variant " + variant.getSku() + " with batch " + itemDto.getBatchNumber() + " was not received in Goods Receipt #" + receiving.getId());
        }

        int alreadyReturned = purchaseReturnRepository.sumAlreadyReturnedQty(receiving.getId(), variant.getId(), itemDto.getBatchNumber(), shopId);
        int totalProposedReturn = alreadyReturned + itemDto.getQuantity();

        if (totalProposedReturn > originallyReceived) {
            throw new BusinessValidationException(
                    "Cannot return " + itemDto.getQuantity() + " units for item " + variant.getSku() +
                    " (Batch: " + itemDto.getBatchNumber() + "). Originally received: " + originallyReceived +
                    ", already returned: " + alreadyReturned + ", available to return: " + (originallyReceived - alreadyReturned));
        }
    }

    private String generateReturnNo() {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        return "PR-" + timestamp;
    }
}
