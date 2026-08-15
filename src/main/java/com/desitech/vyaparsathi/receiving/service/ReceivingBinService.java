package com.desitech.vyaparsathi.receiving.service;

import com.desitech.vyaparsathi.common.exception.BusinessValidationException;
import com.desitech.vyaparsathi.common.exception.ResourceNotFoundException;
import com.desitech.vyaparsathi.common.util.TenantUtils;
import com.desitech.vyaparsathi.receiving.entity.ReceivingBinAssignment;
import com.desitech.vyaparsathi.receiving.entity.ReceivingItem;
import com.desitech.vyaparsathi.receiving.repository.ReceivingBinAssignmentRepository;
import com.desitech.vyaparsathi.shop.entity.Shop;
import com.desitech.vyaparsathi.shop.repository.ShopRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Split-putaway destinations per receiving line. Multiple bin rows per line
 * are permitted — supports a 100-unit line landing 60 in BIN-A and 40 in
 * BIN-B. Guardrails: total assigned ≤ accepted qty on the parent line.
 */
@Service
public class ReceivingBinService {

    private final ReceivingBinAssignmentRepository repository;
    private final ShopRepository shopRepository;

    @PersistenceContext
    private EntityManager em;

    public ReceivingBinService(ReceivingBinAssignmentRepository repository, ShopRepository shopRepository) {
        this.repository = repository;
        this.shopRepository = shopRepository;
    }

    @Transactional
    public ReceivingBinAssignment assign(Long receivingItemId, String binCode, int quantity, String assignedBy) {
        if (binCode == null || binCode.isBlank()) {
            throw new BusinessValidationException("Bin code is required.");
        }
        if (quantity <= 0) {
            throw new BusinessValidationException("Quantity must be positive.");
        }
        ReceivingItem it = em.find(ReceivingItem.class, receivingItemId);
        if (it == null) {
            throw new ResourceNotFoundException("Receiving item not found: " + receivingItemId);
        }

        int already = repository.findByReceivingItemId(receivingItemId).stream()
                .mapToInt(a -> a.getQuantity() != null ? a.getQuantity() : 0)
                .sum();
        if (already + quantity > it.getAcceptedQty()) {
            throw new BusinessValidationException(
                    "Assigning " + quantity + " to bin " + binCode + " would exceed accepted qty (" + it.getAcceptedQty() + ").");
        }

        ReceivingBinAssignment a = new ReceivingBinAssignment();
        a.setReceivingItemId(receivingItemId);
        a.setBinCode(binCode.trim().toUpperCase());
        a.setQuantity(quantity);
        a.setAssignedBy(assignedBy);
        a.setAssignedAt(LocalDateTime.now());
        Long shopId = TenantUtils.getCurrentShopId();
        if (shopId != null) {
            Shop shop = shopRepository.findById(shopId).orElse(null);
            a.setShop(shop);
        }
        return repository.save(a);
    }

    @Transactional(readOnly = true)
    public List<ReceivingBinAssignment> list(Long receivingItemId) {
        return repository.findByReceivingItemId(receivingItemId);
    }

    @Transactional
    public void remove(Long assignmentId) {
        repository.deleteById(assignmentId);
    }
}
