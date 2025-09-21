package com.desitech.vyaparsathi.purchaseorder.repository;

import com.desitech.vyaparsathi.purchaseorder.entity.PurchaseOrder;
import com.desitech.vyaparsathi.purchaseorder.enums.PurchaseOrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, Long> {
    boolean existsByPoNumber(String poNumber);

    @Query("SELECT po FROM PurchaseOrder po WHERE po.status IN (:includedStatuses)")
    List<PurchaseOrder> findAllByStatusIn(@Param("includedStatuses") List<PurchaseOrderStatus> includedStatuses);
}
