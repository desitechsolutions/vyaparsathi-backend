package com.desitech.vyaparsathi.payroll.repository;

import com.desitech.vyaparsathi.payroll.entity.EsiSlab;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

@Repository
public interface EsiSlabRepository extends JpaRepository<EsiSlab, Long> {
    @Query("SELECT e FROM EsiSlab e WHERE e.shop.id = ?1 AND e.isActive = true AND e.effectiveFrom <= ?2 ORDER BY e.effectiveFrom DESC LIMIT 1")
    Optional<EsiSlab> findActiveSlabForDate(Long shopId, LocalDate date);
}
