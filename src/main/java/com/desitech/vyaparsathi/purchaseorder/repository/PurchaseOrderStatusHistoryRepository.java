package com.desitech.vyaparsathi.purchaseorder.repository;

import com.desitech.vyaparsathi.purchaseorder.entity.PurchaseOrderStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PurchaseOrderStatusHistoryRepository
        extends JpaRepository<PurchaseOrderStatusHistory, Long> {

    List<PurchaseOrderStatusHistory> findByPurchaseOrderIdOrderByChangedAtAsc(Long purchaseOrderId);
}
