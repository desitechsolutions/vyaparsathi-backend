package com.desitech.vyaparsathi.customer.repository;

import com.desitech.vyaparsathi.common.repository.BaseRepository;
import com.desitech.vyaparsathi.customer.entity.Customer;
import com.desitech.vyaparsathi.customer.enums.CustomerType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface CustomerRepository extends BaseRepository<Customer, Long>, JpaSpecificationExecutor<Customer> {
    List<Customer> findByNameContainingIgnoreCase(String name);

    @Query("SELECT c FROM Customer c WHERE c.name LIKE %:q%")
    List<Customer> searchByCustomerPartial(@Param("q") String q);

    @Query("SELECT c FROM Customer c WHERE c.shop.id = :shopId")
    List<Customer> findAllByShopId(@Param("shopId") Long shopId);

    // ─── V105 Enterprise queries ───────────────────────────────────────

    /** Server-side search across name, phone, and email. */
    @Query("SELECT c FROM Customer c WHERE c.shop.id = :shopId AND (" +
            "LOWER(c.name) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
            "c.phone LIKE CONCAT('%', :q, '%') OR " +
            "LOWER(c.email) LIKE LOWER(CONCAT('%', :q, '%')))")
    Page<Customer> searchByQuery(@Param("shopId") Long shopId, @Param("q") String q, Pageable pageable);

    /** KPI: count by active status for a shop. */
    long countByActiveAndShop_Id(Boolean active, Long shopId);

    /** KPI: count by customer type for a shop. */
    long countByCustomerTypeAndShop_Id(CustomerType type, Long shopId);

    /** KPI: customers created this month. */
    @Query("SELECT COUNT(c) FROM Customer c WHERE c.shop.id = :shopId AND c.createdAt >= :since")
    long countNewSince(@Param("shopId") Long shopId, @Param("since") LocalDateTime since);

    /** Paged listing for a shop (no filter — used by the old backward-compat endpoint). */
    Page<Customer> findByShop_Id(Long shopId, Pageable pageable);

    /** Check if customer has any sales (FK guard for hard delete). */
    @Query("SELECT CASE WHEN COUNT(s) > 0 THEN true ELSE false END FROM Sale s WHERE s.customer.id = :customerId")
    boolean hasSales(@Param("customerId") Long customerId);

    /** Check if customer has ledger entries. */
    @Query("SELECT CASE WHEN COUNT(l) > 0 THEN true ELSE false END FROM CustomerLedger l WHERE l.customer.id = :customerId")
    boolean hasLedgerEntries(@Param("customerId") Long customerId);
}

