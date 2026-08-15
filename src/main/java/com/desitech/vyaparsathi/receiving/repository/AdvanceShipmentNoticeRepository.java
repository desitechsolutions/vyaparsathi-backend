package com.desitech.vyaparsathi.receiving.repository;

import com.desitech.vyaparsathi.receiving.entity.AdvanceShipmentNotice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AdvanceShipmentNoticeRepository extends JpaRepository<AdvanceShipmentNotice, Long> {
    List<AdvanceShipmentNotice> findByPurchaseOrderIdOrderByDispatchDateDesc(Long purchaseOrderId);
    List<AdvanceShipmentNotice> findByStatus(String status);
}
