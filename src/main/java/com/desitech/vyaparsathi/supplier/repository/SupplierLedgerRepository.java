package com.desitech.vyaparsathi.supplier.repository;

import com.desitech.vyaparsathi.supplier.entity.SupplierLedger;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SupplierLedgerRepository extends JpaRepository<SupplierLedger, Long> {
    Page<SupplierLedger> findByShopIdAndSupplierIdOrderByTransactionDateDesc(Long shopId, Long supplierId, Pageable pageable);

    @Query("SELECT sl FROM SupplierLedger sl WHERE sl.shop.id = :shopId AND sl.supplier.id = :supplierId ORDER BY sl.id DESC LIMIT 1")
    Optional<SupplierLedger> findLatestEntry(Long shopId, Long supplierId);
}
