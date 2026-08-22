package com.desitech.vyaparsathi.payroll.repository;

import com.desitech.vyaparsathi.payroll.entity.PfSlab;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

@Repository
public interface PfSlabRepository extends JpaRepository<PfSlab, Long> {
    @Query("SELECT p FROM PfSlab p WHERE p.shop.id = ?1 AND p.isActive = true AND p.effectiveFrom <= ?2 ORDER BY p.effectiveFrom DESC LIMIT 1")
    Optional<PfSlab> findActiveSlabForDate(Long shopId, LocalDate date);
}
