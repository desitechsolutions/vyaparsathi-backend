package com.desitech.vyaparsathi.purchaseorder.repository;

import com.desitech.vyaparsathi.purchaseorder.entity.PurchaseOrderApproval;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PurchaseOrderApprovalRepository extends JpaRepository<PurchaseOrderApproval, Long> {
    List<PurchaseOrderApproval> findByPurchaseOrderIdOrderByLevelAsc(Long purchaseOrderId);
}
