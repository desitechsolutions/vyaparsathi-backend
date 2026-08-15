package com.desitech.vyaparsathi.purchaseorder.repository;

import com.desitech.vyaparsathi.purchaseorder.entity.PoFieldAudit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PoFieldAuditRepository extends JpaRepository<PoFieldAudit, Long> {
    List<PoFieldAudit> findByPurchaseOrderIdOrderByChangedAtDesc(Long purchaseOrderId);
}
