package com.desitech.vyaparsathi.supplier.repository;

import com.desitech.vyaparsathi.common.repository.BaseRepository;
import com.desitech.vyaparsathi.supplier.entity.SupplierPayment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;

public interface SupplierPaymentRepository extends BaseRepository<SupplierPayment, Long> {

    Page<SupplierPayment> findByPurchaseOrderId(Long purchaseOrderId, Pageable pageable);

    @Query("SELECT COALESCE(SUM(sp.amount), 0) FROM SupplierPayment sp WHERE sp.purchaseOrderId = :purchaseOrderId")
    BigDecimal sumByPurchaseOrderId(@Param("purchaseOrderId") Long purchaseOrderId);
}
