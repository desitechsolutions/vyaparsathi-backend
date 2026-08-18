package com.desitech.vyaparsathi.customer.repository;

import com.desitech.vyaparsathi.common.repository.BaseRepository;
import com.desitech.vyaparsathi.customer.entity.Customer;
import com.desitech.vyaparsathi.customer.entity.CustomerLedger;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface CustomerLedgerRepository extends BaseRepository<CustomerLedger, Long> {
    List<CustomerLedger> findByCustomerOrderByCreatedAtDesc(Customer customer);

    @Query("SELECT l FROM CustomerLedger l WHERE l.customer = :customer " +
            "AND (:startDate IS NULL OR l.createdAt >= :startDate) " +
            "AND (:endDate IS NULL OR l.createdAt <= :endDate) " +
            "ORDER BY l.createdAt DESC")
    List<CustomerLedger> findByCustomerAndDateRange(@Param("customer") Customer customer,
                                                    @Param("startDate") LocalDateTime startDate,
                                                    @Param("endDate") LocalDateTime endDate);

    /**
     * Same as {@link #findByCustomerAndDateRange} but keyed by
     * customer id — used by the enterprise statement builder so
     * callers don't have to fetch the Customer entity just to walk
     * its ledger rows.
     */
    @Query("SELECT l FROM CustomerLedger l WHERE l.customer.id = :customerId " +
            "AND (:startDate IS NULL OR l.createdAt >= :startDate) " +
            "AND (:endDate IS NULL OR l.createdAt <= :endDate) " +
            "ORDER BY l.createdAt DESC")
    List<CustomerLedger> findByCustomerIdAndDateRange(@Param("customerId") Long customerId,
                                                     @Param("startDate") LocalDateTime startDate,
                                                     @Param("endDate") LocalDateTime endDate);
}
