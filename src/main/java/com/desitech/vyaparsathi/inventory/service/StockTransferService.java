package com.desitech.vyaparsathi.inventory.service;

import com.desitech.vyaparsathi.common.configs.TenantContext;
import com.desitech.vyaparsathi.common.exception.BusinessValidationException;
import com.desitech.vyaparsathi.common.exception.EntityNotFoundAppException;
import com.desitech.vyaparsathi.common.exception.InsufficientStockException;
import com.desitech.vyaparsathi.inventory.dto.StockAddDto;
import com.desitech.vyaparsathi.inventory.dto.StockTransferCreateDto;
import com.desitech.vyaparsathi.inventory.dto.StockTransferDto;
import com.desitech.vyaparsathi.inventory.entity.ItemVariant;
import com.desitech.vyaparsathi.inventory.entity.StockTransfer;
import com.desitech.vyaparsathi.inventory.entity.StockTransferItem;
import com.desitech.vyaparsathi.inventory.enums.StockTransferStatus;
import com.desitech.vyaparsathi.inventory.repository.ItemVariantRepository;
import com.desitech.vyaparsathi.inventory.repository.StockTransferRepository;
import com.desitech.vyaparsathi.shop.entity.Shop;
import com.desitech.vyaparsathi.shop.repository.ShopRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Service for creating and executing multi-location stock transfers.
 *
 * <p>A transfer lifecycle:
 * <ol>
 *   <li><b>PENDING</b>   – created but not yet applied (quantities NOT yet moved).</li>
 *   <li><b>COMPLETED</b> – executed: TRANSFER_OUT movements written for the source shop
 *                          and TRANSFER_IN movements written for the destination shop.</li>
 *   <li><b>CANCELLED</b> – voided before execution; no movements recorded.</li>
 * </ol>
 */
@Service
public class StockTransferService {

    private static final Logger log = LoggerFactory.getLogger(StockTransferService.class);
    private static final AtomicLong SEQUENCE = new AtomicLong(System.currentTimeMillis() % 100_000);

    @Autowired
    private StockTransferRepository transferRepository;
    @Autowired
    private ItemVariantRepository variantRepository;
    @Autowired
    private ShopRepository shopRepository;
    @Autowired
    private StockService stockService;

    // -------------------------------------------------------------------------
    // Create
    // -------------------------------------------------------------------------

    /**
     * Creates a new PENDING stock transfer. No stock is moved at this point.
     * Call {@link #executeTransfer(Long)} to actually move the stock.
     *
     * <p>STK-9: fromShopId is always taken from TenantContext (the authenticated
     * user's current shop) and must not be supplied by the caller. The request
     * DTO's fromShopId field is therefore ignored here. toShopId must not equal
     * the current shop to prevent same-shop self-transfers.
     */
    @Transactional
    public StockTransferDto createTransfer(StockTransferCreateDto request) {
        // STK-9: always use the authenticated tenant's shop as source — ignore
        // any fromShopId in the request body to prevent IDOR/cross-tenant stock theft.
        Long currentShopId = TenantContext.getCurrentShopId();
        if (currentShopId == null) {
            throw new com.desitech.vyaparsathi.common.exception.BusinessValidationException("No shop context — cannot create transfer");
        }
        request.setFromShopId(currentShopId);
        validateCreateRequest(request);

        Shop fromShop = shopRepository.findById(request.getFromShopId())
                .orElseThrow(() -> new EntityNotFoundAppException("Shop", request.getFromShopId()));
        Shop toShop = shopRepository.findById(request.getToShopId())
                .orElseThrow(() -> new EntityNotFoundAppException("Shop", request.getToShopId()));

        StockTransfer transfer = new StockTransfer();
        transfer.setTransferNumber(generateTransferNumber());
        transfer.setFromShop(fromShop);
        transfer.setToShop(toShop);
        transfer.setTransferDate(request.getTransferDate() != null ? request.getTransferDate() : LocalDateTime.now());
        transfer.setStatus(StockTransferStatus.PENDING);
        transfer.setNotes(request.getNotes());

        for (StockTransferCreateDto.StockTransferLineDto line : request.getItems()) {
            ItemVariant variant = variantRepository.findById(line.getItemVariantId())
                    .orElseThrow(() -> new EntityNotFoundAppException("ItemVariant", line.getItemVariantId()));

            StockTransferItem item = new StockTransferItem();
            item.setStockTransfer(transfer);
            item.setItemVariant(variant);
            item.setQuantity(line.getQuantity());
            item.setBatchNumber(line.getBatchNumber());
            transfer.getItems().add(item);
        }

        StockTransfer saved = transferRepository.save(transfer);
        log.info("Created stock transfer {} from shop {} to shop {}",
                saved.getTransferNumber(), fromShop.getId(), toShop.getId());
        return toDto(saved);
    }

    // -------------------------------------------------------------------------
    // Execute
    // -------------------------------------------------------------------------

    /**
     * Executes a PENDING transfer (immediate path — no in-transit phase):
     * <ol>
     *   <li>Validates source shop has sufficient stock for every line item.</li>
     *   <li>Records TRANSFER_OUT movements in the source shop.</li>
     *   <li>Records TRANSFER_IN movements in the destination shop.</li>
     *   <li>Marks the transfer as COMPLETED.</li>
     * </ol>
     *
     * <p>TenantContext is temporarily switched per movement and always
     * restored to the original value in a finally block so that subsequent
     * repository calls in the same request thread are not affected (STK-4).
     */
    @Transactional
    public StockTransferDto executeTransfer(Long transferId) {
        StockTransfer transfer = transferRepository.findById(transferId)
                .orElseThrow(() -> new EntityNotFoundAppException("StockTransfer", transferId));

        if (transfer.getStatus() != StockTransferStatus.PENDING) {
            throw new BusinessValidationException(
                    "Transfer " + transfer.getTransferNumber() + " is already " + transfer.getStatus());
        }

        Long fromShopId = transfer.getFromShop().getId();
        Long toShopId   = transfer.getToShop().getId();
        Long originalShopId = TenantContext.getCurrentShopId();

        // --- Phase 1: Validate all lines have sufficient stock in source shop ---
        for (StockTransferItem line : transfer.getItems()) {
            BigDecimal available = stockService.getCurrentStock(line.getItemVariant().getId());
            if (available.compareTo(line.getQuantity()) < 0) {
                throw new InsufficientStockException(
                        "Insufficient stock for variant " + line.getItemVariant().getSku()
                        + ". Available: " + available + ", requested: " + line.getQuantity());
            }
        }

        // --- Phase 2: Record movements (switch TenantContext per direction) ---
        String note = "Transfer " + transfer.getTransferNumber();
        String reference = "Stock Transfer";

        try {
            for (StockTransferItem line : transfer.getItems()) {
                ItemVariant variant = line.getItemVariant();
                BigDecimal qty = line.getQuantity();

                // TRANSFER_OUT: deduct from source shop
                TenantContext.setCurrentShopId(fromShopId);
                stockService.deductStock(variant.getId(), qty, note, reference);

                // TRANSFER_IN: add to destination shop
                TenantContext.setCurrentShopId(toShopId);
                StockAddDto addDto = new StockAddDto();
                addDto.setItemVariantId(variant.getId());
                addDto.setQuantity(qty);
                addDto.setBatch(line.getBatchNumber());
                addDto.setCostPerUnit(BigDecimal.ZERO); // cost stays in source; zero-cost receipt at destination
                stockService.addStockFromDto(addDto);
            }
        } finally {
            // STK-4: Always restore original TenantContext so subsequent calls in
            // the same request thread are scoped to the correct shop.
            TenantContext.setCurrentShopId(originalShopId);
        }

        // --- Phase 3: Mark completed ---
        transfer.setStatus(StockTransferStatus.COMPLETED);
        StockTransfer saved = transferRepository.save(transfer);
        log.info("Executed stock transfer {} – {} lines processed", saved.getTransferNumber(), transfer.getItems().size());
        return toDto(saved);
    }

    // -------------------------------------------------------------------------
    // Cancel
    // -------------------------------------------------------------------------

    /**
     * Cancels a PENDING transfer without moving any stock.
     */
    @Transactional
    public StockTransferDto cancelTransfer(Long transferId) {
        StockTransfer transfer = transferRepository.findById(transferId)
                .orElseThrow(() -> new EntityNotFoundAppException("StockTransfer", transferId));

        if (transfer.getStatus() != StockTransferStatus.PENDING) {
            throw new BusinessValidationException(
                    "Only PENDING transfers can be cancelled. Current status: " + transfer.getStatus());
        }

        transfer.setStatus(StockTransferStatus.CANCELLED);
        StockTransfer saved = transferRepository.save(transfer);
        log.info("Cancelled stock transfer {}", saved.getTransferNumber());
        return toDto(saved);
    }

    // -------------------------------------------------------------------------
    // Query
    // -------------------------------------------------------------------------

    /** Returns all transfers for the current tenant's shop (either sender or receiver). */
    public List<StockTransferDto> getTransfersForCurrentShop() {
        Long shopId = TenantContext.getCurrentShopId();
        return transferRepository.findAllByShopId(shopId)
                .stream().map(this::toDto).collect(Collectors.toList());
    }

    /** Returns a single transfer by ID. */
    public StockTransferDto getTransfer(Long transferId) {
        return toDto(transferRepository.findById(transferId)
                .orElseThrow(() -> new EntityNotFoundAppException("StockTransfer", transferId)));
    }

    /** Returns count of PENDING transfers (for dashboard badge). */
    public long getPendingCount() {
        return transferRepository.countByStatus(StockTransferStatus.PENDING);
    }

    // -------------------------------------------------------------------------
    // V94 Approval + in-transit lifecycle
    // -------------------------------------------------------------------------

    /**
     * Moves a PENDING transfer into PENDING_APPROVAL if the shop policy demands
     * it. Approval is required whenever any line's value ≥ shop's adjustment
     * approval threshold. Otherwise the caller can execute directly.
     */
    @Transactional
    public StockTransferDto requestApproval(Long transferId, String note) {
        StockTransfer transfer = transferRepository.findById(transferId)
                .orElseThrow(() -> new EntityNotFoundAppException("StockTransfer", transferId));
        if (transfer.getStatus() != StockTransferStatus.PENDING) {
            throw new BusinessValidationException("Only PENDING transfers can request approval.");
        }
        transfer.setStatus(StockTransferStatus.PENDING_APPROVAL);
        transfer.setApprovalNote(note);
        return toDto(transferRepository.save(transfer));
    }

    @Transactional
    public StockTransferDto approve(Long transferId, Long approverUserId, String note) {
        StockTransfer transfer = transferRepository.findById(transferId)
                .orElseThrow(() -> new EntityNotFoundAppException("StockTransfer", transferId));
        if (transfer.getStatus() != StockTransferStatus.PENDING_APPROVAL
                && transfer.getStatus() != StockTransferStatus.PENDING) {
            throw new BusinessValidationException("Only PENDING/PENDING_APPROVAL transfers can be approved.");
        }
        transfer.setApprovedBy(approverUserId);
        transfer.setApprovedAt(LocalDateTime.now());
        if (note != null) transfer.setApprovalNote(note);
        // Approved but not yet dispatched — remains PENDING for the executor.
        transfer.setStatus(StockTransferStatus.PENDING);
        return toDto(transferRepository.save(transfer));
    }

    /**
     * Marks an approved transfer as dispatched — deducts stock from origin and
     * sets status to IN_TRANSIT. Stock is credited to the destination shop only
     * when the receiving shop calls {@link #confirmArrival(Long)} (STK-1 fix).
     *
     * <p>TenantContext is always restored to its original value (STK-4 fix).
     */
    @Transactional
    public StockTransferDto dispatch(Long transferId) {
        StockTransfer transfer = transferRepository.findById(transferId)
                .orElseThrow(() -> new EntityNotFoundAppException("StockTransfer", transferId));
        if (transfer.getStatus() != StockTransferStatus.PENDING) {
            throw new BusinessValidationException("Only PENDING transfers can be dispatched.");
        }

        Long fromShopId = transfer.getFromShop().getId();
        Long originalShopId = TenantContext.getCurrentShopId();
        String note = "Dispatch " + transfer.getTransferNumber();
        String reference = "Stock Transfer";

        // Validate sufficient stock at origin before touching any state
        for (StockTransferItem line : transfer.getItems()) {
            BigDecimal available = stockService.getCurrentStock(line.getItemVariant().getId());
            if (available.compareTo(line.getQuantity()) < 0) {
                throw new InsufficientStockException(
                        "Insufficient stock for variant " + line.getItemVariant().getSku()
                        + ". Available: " + available + ", requested: " + line.getQuantity());
            }
        }

        try {
            // TRANSFER_OUT only — credit happens at confirmArrival
            TenantContext.setCurrentShopId(fromShopId);
            for (StockTransferItem line : transfer.getItems()) {
                stockService.deductStock(line.getItemVariant().getId(), line.getQuantity(), note, reference);
            }
        } finally {
            TenantContext.setCurrentShopId(originalShopId);
        }

        transfer.setStatus(StockTransferStatus.IN_TRANSIT);
        transfer.setInTransitAt(LocalDateTime.now());
        StockTransfer saved = transferRepository.save(transfer);
        log.info("Dispatched stock transfer {} – stock deducted from shop {}", saved.getTransferNumber(), fromShopId);
        return toDto(saved);
    }

    /**
     * Confirms receipt at the destination — credits stock into the destination
     * shop and marks the transfer COMPLETED (STK-1 fix: stock arrives here,
     * not at dispatch time).
     */
    @Transactional
    public StockTransferDto confirmArrival(Long transferId) {
        StockTransfer transfer = transferRepository.findById(transferId)
                .orElseThrow(() -> new EntityNotFoundAppException("StockTransfer", transferId));
        if (transfer.getStatus() != StockTransferStatus.IN_TRANSIT) {
            throw new BusinessValidationException("Only IN_TRANSIT transfers can be received.");
        }

        Long toShopId = transfer.getToShop().getId();
        Long originalShopId = TenantContext.getCurrentShopId();
        String note = "Receipt " + transfer.getTransferNumber();

        try {
            TenantContext.setCurrentShopId(toShopId);
            for (StockTransferItem line : transfer.getItems()) {
                StockAddDto addDto = new StockAddDto();
                addDto.setItemVariantId(line.getItemVariant().getId());
                addDto.setQuantity(line.getQuantity());
                addDto.setBatch(line.getBatchNumber());
                addDto.setCostPerUnit(BigDecimal.ZERO);
                stockService.addStockFromDto(addDto);
            }
        } finally {
            TenantContext.setCurrentShopId(originalShopId);
        }

        transfer.setStatus(StockTransferStatus.COMPLETED);
        transfer.setReceivedAt(LocalDateTime.now());
        StockTransfer saved = transferRepository.save(transfer);
        log.info("Confirmed arrival for stock transfer {} – stock credited to shop {}", saved.getTransferNumber(), toShopId);
        return toDto(saved);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void validateCreateRequest(StockTransferCreateDto req) {
        if (req.getFromShopId() == null || req.getToShopId() == null) {
            throw new BusinessValidationException("fromShopId and toShopId are required");
        }
        if (req.getFromShopId().equals(req.getToShopId())) {
            throw new BusinessValidationException("Source and destination shops must be different");
        }
        if (req.getItems() == null || req.getItems().isEmpty()) {
            throw new BusinessValidationException("At least one line item is required");
        }
        for (StockTransferCreateDto.StockTransferLineDto line : req.getItems()) {
            if (line.getQuantity() == null || line.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessValidationException("Each line item must have a positive quantity");
            }
        }
    }

    private String generateTransferNumber() {
        String datePart = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        return "TRF-" + datePart + "-" + String.format("%05d", SEQUENCE.incrementAndGet() % 100_000);
    }

    private StockTransferDto toDto(StockTransfer transfer) {
        StockTransferDto dto = new StockTransferDto();
        dto.setId(transfer.getId());
        dto.setTransferNumber(transfer.getTransferNumber());
        dto.setFromShopId(transfer.getFromShop().getId());
        dto.setFromShopName(transfer.getFromShop().getName());
        dto.setToShopId(transfer.getToShop().getId());
        dto.setToShopName(transfer.getToShop().getName());
        dto.setTransferDate(transfer.getTransferDate());
        dto.setStatus(transfer.getStatus());
        dto.setNotes(transfer.getNotes());
        dto.setCreatedAt(transfer.getCreatedAt());

        List<StockTransferDto.LineItemDto> lines = transfer.getItems().stream().map(line -> {
            StockTransferDto.LineItemDto ld = new StockTransferDto.LineItemDto();
            ld.setItemVariantId(line.getItemVariant().getId());
            ld.setItemName(line.getItemVariant().getItem() != null ? line.getItemVariant().getItem().getName() : null);
            ld.setSku(line.getItemVariant().getSku());
            ld.setQuantity(line.getQuantity());
            ld.setBatchNumber(line.getBatchNumber());
            return ld;
        }).collect(Collectors.toList());

        dto.setItems(lines);
        return dto;
    }
}
