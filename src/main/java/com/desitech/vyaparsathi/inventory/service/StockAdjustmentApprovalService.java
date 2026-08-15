package com.desitech.vyaparsathi.inventory.service;

import com.desitech.vyaparsathi.common.exception.BusinessValidationException;
import com.desitech.vyaparsathi.common.exception.ResourceNotFoundException;
import com.desitech.vyaparsathi.common.util.TenantUtils;
import com.desitech.vyaparsathi.inventory.entity.StockAdjustmentApproval;
import com.desitech.vyaparsathi.inventory.repository.StockAdjustmentApprovalRepository;
import com.desitech.vyaparsathi.shop.entity.Shop;
import com.desitech.vyaparsathi.shop.repository.ShopRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Governs high-value stock adjustments. Callers hand off the intended delta
 * qty + value; if the shop has {@code adjustmentApprovalThreshold} set and
 * the value crosses it, an approval envelope is persisted in {@code PENDING}
 * and the caller receives back the envelope for a follow-up approve/reject
 * decision. When no threshold is set, {@link #requires(BigDecimal)} returns
 * false and the caller commits the adjustment directly.
 */
@Service
public class StockAdjustmentApprovalService {

    private final StockAdjustmentApprovalRepository repo;
    private final ShopRepository shopRepository;

    public StockAdjustmentApprovalService(StockAdjustmentApprovalRepository repo,
                                          ShopRepository shopRepository) {
        this.repo = repo;
        this.shopRepository = shopRepository;
    }

    @Transactional(readOnly = true)
    public boolean requires(BigDecimal absoluteValue) {
        if (absoluteValue == null || absoluteValue.signum() <= 0) return false;
        Long shopId = TenantUtils.getCurrentShopId();
        if (shopId == null) return false;
        Shop shop = shopRepository.findById(shopId).orElse(null);
        if (shop == null || shop.getAdjustmentApprovalThreshold() == null) return false;
        BigDecimal threshold = shop.getAdjustmentApprovalThreshold();
        return threshold.signum() > 0 && absoluteValue.compareTo(threshold) >= 0;
    }

    @Transactional
    public StockAdjustmentApproval requestApproval(Long itemVariantId, BigDecimal deltaQty,
                                                   BigDecimal deltaValue, String reason,
                                                   String requestedBy) {
        StockAdjustmentApproval a = new StockAdjustmentApproval();
        a.setItemVariantId(itemVariantId);
        a.setDeltaQty(deltaQty);
        a.setDeltaValue(deltaValue != null ? deltaValue : BigDecimal.ZERO);
        a.setReason(reason);
        a.setRequestedBy(requestedBy);
        a.setRequestedAt(LocalDateTime.now());
        a.setStatus("PENDING");
        Long shopId = TenantUtils.getCurrentShopId();
        if (shopId != null) {
            Shop shop = shopRepository.findById(shopId).orElse(null);
            a.setShop(shop);
        }
        return repo.save(a);
    }

    @Transactional
    public StockAdjustmentApproval approve(Long approvalId, Long userId, String note) {
        StockAdjustmentApproval a = repo.findById(approvalId)
                .orElseThrow(() -> new ResourceNotFoundException("Approval not found: " + approvalId));
        if (!"PENDING".equals(a.getStatus())) {
            throw new BusinessValidationException("Adjustment approval is already " + a.getStatus());
        }
        a.setStatus("APPROVED");
        a.setApprovedBy(userId);
        a.setApprovedAt(LocalDateTime.now());
        a.setNote(note);
        return repo.save(a);
    }

    @Transactional
    public StockAdjustmentApproval reject(Long approvalId, Long userId, String note) {
        StockAdjustmentApproval a = repo.findById(approvalId)
                .orElseThrow(() -> new ResourceNotFoundException("Approval not found: " + approvalId));
        if (!"PENDING".equals(a.getStatus())) {
            throw new BusinessValidationException("Adjustment approval is already " + a.getStatus());
        }
        a.setStatus("REJECTED");
        a.setApprovedBy(userId);
        a.setApprovedAt(LocalDateTime.now());
        a.setNote(note);
        return repo.save(a);
    }

    @Transactional(readOnly = true)
    public List<StockAdjustmentApproval> pending() {
        return repo.findByStatusOrderByRequestedAtDesc("PENDING");
    }
}
