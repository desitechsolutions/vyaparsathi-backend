package com.desitech.vyaparsathi.purchases.repository;

import com.desitech.vyaparsathi.purchases.entity.PurchaseInvoice;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface PurchaseInvoiceRepository extends JpaRepository<PurchaseInvoice, Long> {
    Page<PurchaseInvoice> findAllByShopId(Long shopId, Pageable pageable);
    Optional<PurchaseInvoice> findByShopIdAndPurchaseInvoiceNo(Long shopId, String purchaseInvoiceNo);
    Page<PurchaseInvoice> findByShopIdAndSupplierId(Long shopId, Long supplierId, Pageable pageable);

    /**
     * Purchases within a date range for a shop — used by GSTR-3B and other
     * period-scoped reports. Replaces the earlier {@code Pageable.unpaged()}
     * pattern that loaded every historical purchase before in-memory filtering.
     */
    List<PurchaseInvoice> findAllByShopIdAndPurchaseDateBetween(Long shopId, LocalDate from, LocalDate to);
}
