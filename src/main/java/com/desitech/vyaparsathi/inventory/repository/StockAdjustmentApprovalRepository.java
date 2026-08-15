package com.desitech.vyaparsathi.inventory.repository;

import com.desitech.vyaparsathi.inventory.entity.StockAdjustmentApproval;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StockAdjustmentApprovalRepository extends JpaRepository<StockAdjustmentApproval, Long> {
    List<StockAdjustmentApproval> findByStatusOrderByRequestedAtDesc(String status);
    List<StockAdjustmentApproval> findByItemVariantIdOrderByRequestedAtDesc(Long variantId);
}
